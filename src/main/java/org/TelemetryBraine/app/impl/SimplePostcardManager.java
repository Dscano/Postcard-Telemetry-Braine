package org.TelemetryBraine.app.impl;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Striped;
import org.onlab.util.KryoNamespace;
import org.onlab.util.SharedScheduledExecutors;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.TelemetryBraine.app.api.IntIntent;
import org.TelemetryBraine.app.api.IntIntentId;
import org.TelemetryBraine.app.api.IntService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.codec.CodecService;
import org.TelemetryBraine.app.rest.IntIntentCodec;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.MastershipRole;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.config.basics.SubjectFactories;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.TelemetryBraine.app.postcard.postProgrammable;
import org.TelemetryBraine.app.postcard.IntObjective;
import org.TelemetryBraine.app.postcard.IntReportConfig;
import org.TelemetryBraine.app.postcard.IntMetadataType;
import org.TelemetryBraine.app.postcard.IntDeviceConfig;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.*;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Simple implementation of POSTCARD-Service, for controlling POSTCARD-capable pipelines.
 * The implementation listens for different types of events and when required it
 * configures a device by cleaning-up any previous state and applying the new
 * one.
 */
@Component(immediate = true, service = IntService.class)
public class SimplePostcardManager implements IntService {

    private final Logger log = getLogger(getClass());

    private static final int CONFIG_EVENT_DELAY = 5; // Seconds.

    private static final String APP_NAME = "org.TelemetryBraine.postcardtelemetry";

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigService netcfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry netcfgRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CodecService codecService;

    private final Striped<Lock> deviceLocks = Striped.lock(10);

    private final ConcurrentMap<DeviceId, ScheduledFuture<?>> scheduledDeviceTasks = Maps.newConcurrentMap();

    // Distributed state.
    private ConsistentMap<IntIntentId, IntIntent> intentMap;
    private ConsistentMap<DeviceId, Long> devicesToConfigure;
    private AtomicValue<IntDeviceConfig> intConfig;
    private AtomicValue<Boolean> intStarted;
    private AtomicIdGenerator intentIds;

    // Event listeners.
    private final InternalHostListener hostListener = new InternalHostListener();
    private final InternalDeviceListener deviceListener = new InternalDeviceListener();
    private final InternalIntentMapListener intentMapListener = new InternalIntentMapListener();
    private final InternalIntConfigListener intConfigListener = new InternalIntConfigListener();
    private final InternalIntStartedListener intStartedListener = new InternalIntStartedListener();
    private final InternalDeviceToConfigureListener devicesToConfigureListener =
            new InternalDeviceToConfigureListener();
    private final NetworkConfigListener appConfigListener = new IntAppConfigListener();

    private final ConfigFactory<ApplicationId, IntReportConfig> intAppConfigFactory =
            new ConfigFactory<>(SubjectFactories.APP_SUBJECT_FACTORY,
                    IntReportConfig.class, "report") {
                @Override
                public IntReportConfig createConfig() {
                    return new IntReportConfig();
                }
            };

    protected ExecutorService eventExecutor;

    @Activate
    public void activate() {

        final ApplicationId appId = coreService.registerApplication(APP_NAME);

        KryoNamespace.Builder serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(IntIntent.class)
                .register(IntIntentId.class)
                .register(IntDeviceRole.class)
                .register(IntIntent.IntHeaderType.class)
                .register(IntMetadataType.class)
                .register(IntIntent.IntReportType.class)
                .register(IntIntent.TelemetryMode.class)
                .register(IntDeviceConfig.class)
                .register(IntDeviceConfig.TelemetrySpec.class);
        codecService.registerCodec(IntIntent.class, new IntIntentCodec());
        devicesToConfigure = storageService.<DeviceId, Long>consistentMapBuilder()
                .withSerializer(Serializer.using(serializer.build()))
                .withName("onos-postcard-devices-to-configure")
                .withApplicationId(appId)
                .withPurgeOnUninstall()
                .build();
        devicesToConfigure.addListener(devicesToConfigureListener);

        intentMap = storageService.<IntIntentId, IntIntent>consistentMapBuilder()
                .withSerializer(Serializer.using(serializer.build()))
                .withName("onos-postcard-intents")
                .withApplicationId(appId)
                .withPurgeOnUninstall()
                .build();
        intentMap.addListener(intentMapListener);

        intStarted = storageService.<Boolean>atomicValueBuilder()
                .withSerializer(Serializer.using(serializer.build()))
                .withName("onos-postcard-started")
                .withApplicationId(appId)
                .build()
                .asAtomicValue();
        intStarted.addListener(intStartedListener);

        intConfig = storageService.<IntDeviceConfig>atomicValueBuilder()
                .withSerializer(Serializer.using(serializer.build()))
                .withName("onos-postcard-config")
                .withApplicationId(appId)
                .build()
                .asAtomicValue();
        intConfig.addListener(intConfigListener);

        intentIds = storageService.getAtomicIdGenerator("postcard-intent-id-generator");

        // Bootstrap config for already existing devices.
        triggerAllDeviceConfigure();

        // Bootstrap core event executor before adding listener
        eventExecutor = newSingleThreadScheduledExecutor(groupedThreads(
                "onos/postcard", "events-%d", log));

        hostService.addListener(hostListener);
        deviceService.addListener(deviceListener);

        netcfgRegistry.registerConfigFactory(intAppConfigFactory);
        netcfgService.addListener(appConfigListener);
        // Initialize the INT report
        IntReportConfig reportConfig = netcfgService.getConfig(appId, IntReportConfig.class);
        if (reportConfig != null) {
            IntDeviceConfig intDeviceConfig = IntDeviceConfig.builder()
                    .withMinFlowHopLatencyChangeNs(reportConfig.minFlowHopLatencyChangeNs())
                    .withCollectorPort(reportConfig.collectorPort())
                    .withCollectorIp(reportConfig.collectorIp())
                    .enabled(true)
                    .build();
            setConfig(intDeviceConfig);
        }

        startInt();
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        deviceService.removeListener(deviceListener);
        hostService.removeListener(hostListener);
        intentIds = null;
        intConfig.removeListener(intConfigListener);
        intConfig = null;
        intStarted.removeListener(intStartedListener);
        intStarted = null;
        intentMap.removeListener(intentMapListener);
        intentMap = null;
        devicesToConfigure.removeListener(devicesToConfigureListener);
        devicesToConfigure.destroy();
        devicesToConfigure = null;
        codecService.unregisterCodec(IntIntent.class);
        // Cancel tasks (if any).
        scheduledDeviceTasks.values().forEach(f -> {
            f.cancel(true);
            if (!f.isDone()) {
                try {
                    f.get(1, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    // Don't care, we are terminating the service anyways.
                }
            }
        });
        // Clean up INT rules from existing devices.
        deviceService.getDevices().forEach(d -> cleanupDevice(d.id()));
        netcfgService.removeListener(appConfigListener);
        netcfgRegistry.unregisterConfigFactory(intAppConfigFactory);
        eventExecutor.shutdownNow();
        eventExecutor = null;
        log.info("Deactivated");
    }

    @Override
    public void startInt() {
        // Atomic value event will trigger device configure.
        intStarted.set(true);
    }

    @Override
    public void startInt(Set<DeviceId> deviceIds) {
        log.warn("Starting INT for a subset of devices is not supported");
    }

    @Override
    public void stopInt() {
        // Atomic value event will trigger device configure.
        intStarted.set(false);
    }

    @Override
    public void stopInt(Set<DeviceId> deviceIds) {
        log.warn("Stopping INT for a subset of devices is not supported");
    }

    @Override
    public void setConfig(IntDeviceConfig cfg) {
        checkNotNull(cfg);
        // Atomic value event will trigger device configure.
        intConfig.set(cfg);
    }

    @Override
    public IntDeviceConfig getConfig() {
        return intConfig.get();
    }

    @Override
    public IntIntentId installIntIntent(IntIntent intent) {
        checkNotNull(intent);
        final Integer intentId = (int) intentIds.nextId();
        final IntIntentId intIntentId = IntIntentId.valueOf(intentId);
        // Intent map event will trigger device configure.
        intentMap.put(intIntentId, intent);
        return intIntentId;
    }

    @Override
    public void removeIntIntent(IntIntentId intentId) {
        checkNotNull(intentId);
        // Intent map event will trigger device configure.
        if (!intentMap.containsKey(intentId)) {
            log.warn("INT intent {} does not exists, skip removing the intent.", intentId);
            return;
        }
        intentMap.remove(intentId);
    }

    @Override
    public IntIntent getIntIntent(IntIntentId intentId) {
        return Optional.ofNullable(intentMap.get(intentId).value()).orElse(null);
    }

    @Override
    public Map<IntIntentId, IntIntent> getIntIntents() {
        return intentMap.asJavaMap();
    }

    private boolean isConfigTaskValid(DeviceId deviceId, long creationTime) {
        Versioned<?> versioned = devicesToConfigure.get(deviceId);
        return versioned != null && versioned.creationTime() == creationTime;
    }

    private boolean isNotIntConfigured() {
        return intConfig.get() == null;
    }

    private boolean isIntProgrammable(DeviceId deviceId) {
        final Device device = deviceService.getDevice(deviceId);
        return device != null && device.is(postProgrammable.class);
    }

    private void triggerDeviceConfigure(DeviceId deviceId) {
        if (isIntProgrammable(deviceId)) {
            devicesToConfigure.put(deviceId, System.nanoTime());
        }
    }

    private void triggerAllDeviceConfigure() {
        deviceService.getDevices().forEach(d -> triggerDeviceConfigure(d.id()));
    }

    private void configDeviceTask(DeviceId deviceId, long creationTime) {
        if (isConfigTaskValid(deviceId, creationTime)) {
            // Task outdated.
            return;
        }
        if (!deviceService.isAvailable(deviceId)) {
            return;
        }
        final MastershipRole role = mastershipService.requestRoleForSync(deviceId);
        if (!role.equals(MastershipRole.MASTER)) {
            return;
        }
        deviceLocks.get(deviceId).lock();
        try {
            // Clean up first.
            cleanupDevice(deviceId);
            if (!configDevice(deviceId)) {
                // Clean up if fails.
                cleanupDevice(deviceId);
                return;
            }
            devicesToConfigure.remove(deviceId);
        } finally {
            deviceLocks.get(deviceId).unlock();
        }
    }

    private void cleanupDevice(DeviceId deviceId) {
        final Device device = deviceService.getDevice(deviceId);
        if (device == null || !device.is(postProgrammable.class)) {
            return;
        }
        device.as(postProgrammable.class).cleanup();
    }

    protected boolean configDevice(DeviceId deviceId) {
        // Returns true if config was successful, false if not and a clean up is
        // needed.

        final Device device = deviceService.getDevice(deviceId);
        if (device == null || !device.is(postProgrammable.class)) {
            return true;
        }

        if (isNotIntConfigured()) {
            log.warn("Missing INT config, aborting programming of INT device {}", deviceId);
            return true;
        }

        final postProgrammable postProg = device.as(postProgrammable.class);

        boolean supportPostcard = postProg.supportsFunctionality(postProgrammable.IntFunctionality.POSTCARD);
        postProg.setupIntConfig(intConfig.get());

        if(!hostService.getConnectedHosts(deviceId).isEmpty()){
            postProg.initController(deviceId);
        }

        // Apply intents.
        // This is a trivial implementation where we simply get the
        // corresponding INT objective from an intent and we apply to all
        // device which support reporting.
        int appliedCount = 0;
        for (Versioned<IntIntent> versionedIntent : intentMap.values()) {
            IntIntent intent = versionedIntent.value();
            IntObjective intObjective = getIntObjective(intent);
            if (intent.telemetryMode() == IntIntent.TelemetryMode.POSTCARD && supportPostcard) {
                postProg.addIntObjective(intObjective);
                appliedCount++;
            } else {
                log.warn("Device {} does not support intent {}.", deviceId, intent);
            }
        }
        log.info("Completed programming of {}, applied {} INT objectives of {} total",
                deviceId, appliedCount, intentMap.size());
        return true;
    }

    private IntObjective getIntObjective(IntIntent intent) {
        return new IntObjective.Builder()
                .withSelector(intent.selector())
                .withMetadataTypes(intent.metadataTypes())
                .withFlowId(intent.flowId())
                .build();
    }

    /* Event listeners which trigger device configuration. */
    private class InternalHostListener implements HostListener {
        @Override
        public void event(HostEvent event) {
            eventExecutor.execute(() -> {
                final DeviceId deviceId = event.subject().location().deviceId();
                triggerDeviceConfigure(deviceId);
            });
        }
    }

    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            eventExecutor.execute(() -> {
                switch (event.type()) {
                    case DEVICE_ADDED:
                    case DEVICE_UPDATED:
                    case DEVICE_REMOVED:
                    case DEVICE_SUSPENDED:
                    case DEVICE_AVAILABILITY_CHANGED:
                    case PORT_ADDED:
                    case PORT_UPDATED:
                    case PORT_REMOVED:
                        triggerDeviceConfigure(event.subject().id());
                        return;
                    case PORT_STATS_UPDATED:
                        return;
                    default:
                        log.warn("Unknown device event type {}", event.type());
                }
            });
        }
    }

    private class InternalIntentMapListener
            implements MapEventListener<IntIntentId, IntIntent> {
        @Override
        public void event(MapEvent<IntIntentId, IntIntent> event) {
            triggerAllDeviceConfigure();
        }
    }

    private class InternalIntConfigListener
            implements AtomicValueEventListener<IntDeviceConfig> {
        @Override
        public void event(AtomicValueEvent<IntDeviceConfig> event) {
            triggerAllDeviceConfigure();
        }
    }

    private class InternalIntStartedListener
            implements AtomicValueEventListener<Boolean> {
        @Override
        public void event(AtomicValueEvent<Boolean> event) {
            triggerAllDeviceConfigure();
        }
    }

    private class InternalDeviceToConfigureListener
            implements MapEventListener<DeviceId, Long> {
        @Override
        public void event(MapEvent<DeviceId, Long> event) {
            if (event.type().equals(MapEvent.Type.REMOVE) ||
                    event.newValue() == null) {
                return;
            }
            // Schedule task in the future. Wait for events for this device to
            // stabilize.
            final DeviceId deviceId = event.key();
            final long creationTime = event.newValue().creationTime();
            ScheduledFuture<?> newTask = SharedScheduledExecutors.newTimeout(
                    () -> configDeviceTask(deviceId, creationTime),
                    CONFIG_EVENT_DELAY, TimeUnit.SECONDS);
            ScheduledFuture<?> oldTask = scheduledDeviceTasks.put(deviceId, newTask);
            if (oldTask != null) {
                oldTask.cancel(false);
            }
        }
    }

    private class IntAppConfigListener implements NetworkConfigListener {

        @Override
        public void event(NetworkConfigEvent event) {
            eventExecutor.execute(() -> {
                if (event.configClass() == IntReportConfig.class) {
                    switch (event.type()) {
                        case CONFIG_ADDED:
                        case CONFIG_UPDATED:
                            event.config()
                                    .map(config -> (IntReportConfig) config)
                                    .ifPresent(config -> {
                                        IntDeviceConfig intDeviceConfig = IntDeviceConfig.builder()
                                                .withMinFlowHopLatencyChangeNs(config.minFlowHopLatencyChangeNs())
                                                .withCollectorPort(config.collectorPort())
                                                .withCollectorIp(config.collectorIp())
                                                .enabled(true)
                                                .build();
                                        setConfig(intDeviceConfig);
                                        // For each watched subnet, we install two INT rules.
                                        // One match on the source, another match on the destination.
                                        intentMap.clear();
                                        config.watchSubnets().forEach(subnet -> {
                                            IntIntent.Builder intIntentBuilder = IntIntent.builder()
                                                    .withReportType(IntIntent.IntReportType.TRACKED_FLOW)
                                                    .withReportType(IntIntent.IntReportType.DROPPED_PACKET)
                                                    .withReportType(IntIntent.IntReportType.CONGESTED_QUEUE)
                                                    .withTelemetryMode(IntIntent.TelemetryMode.POSTCARD);
                                            if (subnet.prefixLength() == 0) {
                                                // Special case, match any packet
                                                installIntIntent(intIntentBuilder
                                                        .withSelector(DefaultTrafficSelector.emptySelector())
                                                        .build());
                                            } else {
                                                TrafficSelector selector = DefaultTrafficSelector.builder()
                                                        .matchIPSrc(subnet)
                                                        .build();
                                                installIntIntent(intIntentBuilder.withSelector(selector).build());
                                                selector = DefaultTrafficSelector.builder()
                                                        .matchIPDst(subnet)
                                                        .build();
                                                installIntIntent(intIntentBuilder.withSelector(selector).build());
                                            }
                                        });
                                    });
                            break;
                        // TODO: Support removing INT config.
                        default:
                            break;
                    }
                }
            });
        }

    }
}
