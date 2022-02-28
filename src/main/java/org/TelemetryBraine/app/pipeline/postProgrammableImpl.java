package org.TelemetryBraine.app.pipeline;

import com.google.common.collect.Sets;
import org.TelemetryBraine.app.postcard.IntMetadataType;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.TelemetryBraine.app.postcard.IntDeviceConfig;
import org.TelemetryBraine.app.postcard.IntObjective;
import org.TelemetryBraine.app.postcard.postProgrammable;
import org.onosproject.net.DeviceId;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TableId;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.flow.criteria.TcpPortCriterion;
import org.onosproject.net.flow.criteria.UdpPortCriterion;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.slf4j.LoggerFactory.getLogger;

public class postProgrammableImpl extends AbstractHandlerBehaviour implements postProgrammable {

    // TODO: change this value to the value of diameter of a network.
    private static final int PORTMASK = 0xffff;
    // Application name of the pipeline which adds this implementation to the pipeconf
    private static final String PIPELINE_APP_NAME = "org.TelemetryBraine.app.pipeline";
    private final Logger log = getLogger(getClass());
    private ApplicationId appId;

    private static final Set<Criterion.Type> SUPPORTED_CRITERION = Sets.newHashSet(
            Criterion.Type.IPV4_DST, Criterion.Type.IPV4_SRC,
            Criterion.Type.UDP_SRC, Criterion.Type.UDP_DST,
            Criterion.Type.TCP_SRC, Criterion.Type.TCP_DST,
            Criterion.Type.IP_PROTO);

    private static final Set<PiTableId> TABLES_TO_CLEANUP = Sets.newHashSet(
            PostcardConstants.INGRESS_PROCESS_ACTIVATE_POSTCARD_TB_POSTCARD_TELEMETRY,
            PostcardConstants.INGRESS_PROCESS_INT_SOURCE_SINK_TB_SET_SOURCE,
            PostcardConstants.INGRESS_PROCESS_INT_SOURCE_SINK_TB_SET_SINK,
            PostcardConstants.EGRESS_PROCESS_POST_META_TB_INT_INSERT,
            PostcardConstants.EGRESS_PROCESS_POSTCARD_REPORT_TB_GENERATE_REPORT);

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private CoreService coreService;

    private DeviceId deviceId;
    private static final int DEFAULT_PRIORITY = 10000;

    private boolean setupBehaviour() {
        deviceId = this.data().deviceId();
        flowRuleService = handler().get(FlowRuleService.class);
        coreService = handler().get(CoreService.class);
        appId = coreService.getAppId(PIPELINE_APP_NAME);
        if (appId == null) {
            log.warn("Application ID is null. Cannot initialize behaviour.");
            return false;
        }
        return true;
    }

    @Override
    public boolean addIntObjective(IntObjective obj) {
        // TODO: support different types of watchlist other than flow watchlist
        return processIntObjective(obj, true);
    }

    @Override
    public boolean removeIntObjective(IntObjective obj) {
        return processIntObjective(obj, false);
    }

    @Override
    public boolean setupIntConfig(IntDeviceConfig config) {
        return setupIntReportInternal(config);
    }

    @Override
    public void cleanup() {
        if (!setupBehaviour()) {
            return;
        }

        StreamSupport.stream(flowRuleService.getFlowEntries(
                data().deviceId()).spliterator(), false)
                .filter(f -> f.table().type() == TableId.Type.PIPELINE_INDEPENDENT)
                .filter(f -> TABLES_TO_CLEANUP.contains((PiTableId) f.table()))
                .forEach(flowRuleService::removeFlowRules);
    }

    @Override
    public boolean supportsFunctionality(IntFunctionality functionality) {
        switch (functionality) {
            case POSTCARD:
                return true;
            default:
                log.warn("Unknown functionality {}", functionality);
                return false;
        }
    }

    private Set<FlowRule> buildWatchlistEntries(IntObjective obj) {
        Set<FlowRule> flowRules = new HashSet<>();

        int instructionBitmap = buildInstructionBitmap(obj.metadataTypes());

        /*TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchPi(PiCriterion.builder().matchExact(
                        PostcardConstants.HDR_REPORT_IS_VALID, (byte) 0x01)
                        .build())
                .build();*/

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();

        for (Criterion criterion : obj.selector().criteria()) {
            switch (criterion.type()) {
                case IPV4_SRC:
                    selector.matchIPSrc(((IPCriterion) criterion).ip());
                    break;
                case IPV4_DST:
                    selector.matchIPDst(((IPCriterion) criterion).ip());
                    break;
                default:
                    log.warn("Unsupported criterion type: {}", criterion.type());
            }
        }

        selector.matchPi(PiCriterion.builder().matchExact(
                PostcardConstants.HDR_REPORT_IS_VALID, (byte) 0x01)
                .build());

        PiActionParam transitSwitchId = new PiActionParam(
                PostcardConstants.SWITCH_ID,
                ImmutableByteSequence.copyFrom(
                        Integer.parseInt(deviceId.toString().substring(
                                deviceId.toString().length() - 2))));

        PiActionParam transitFlowId = new PiActionParam(
                PostcardConstants.FLOW_ID,
                ImmutableByteSequence.copyFrom(obj.flowIf()));

        PiActionParam inst0003Param = new PiActionParam(
                PostcardConstants.INS_MASK0003,
                ImmutableByteSequence.copyFrom((instructionBitmap >> 12) & 0xF));

        PiActionParam inst0407Param = new PiActionParam(
                PostcardConstants.INS_MASK0407,
                ImmutableByteSequence.copyFrom((instructionBitmap >> 8) & 0xF));

        PiAction transitAction = PiAction.builder()
                .withId(PostcardConstants.EGRESS_PROCESS_POST_META_INIT_METADATA)
                .withParameter(transitSwitchId)
                .withParameter(transitFlowId)
                .withParameter(inst0003Param)
                .withParameter(inst0407Param)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .piTableAction(transitAction)
                .build();

        flowRules.add(DefaultFlowRule.builder()
                .withSelector(selector.build())
                .withTreatment(treatment)
                .fromApp(appId)
                .withPriority(DEFAULT_PRIORITY)
                .makePermanent()
                .forDevice(deviceId)
                .forTable(PostcardConstants.EGRESS_PROCESS_POST_META_TB_INT_INSERT)
                .build());

        PiAction intSourceAction = PiAction.builder()
                .withId(PostcardConstants.INGRESS_PROCESS_ACTIVATE_POSTCARD_ACTIVATE_POSTCARD)
                .build();

        TrafficTreatment instTreatment = DefaultTrafficTreatment.builder()
                .piTableAction(intSourceAction)
                .build();

        TrafficSelector.Builder sBuilder = DefaultTrafficSelector.builder();
        for (Criterion criterion : obj.selector().criteria()) {
            switch (criterion.type()) {
                case IPV4_SRC:
                    sBuilder.matchIPSrc(((IPCriterion) criterion).ip());
                    break;
                case IPV4_DST:
                    sBuilder.matchIPDst(((IPCriterion) criterion).ip());
                    break;
                case TCP_SRC:
                    sBuilder.matchPi(
                            PiCriterion.builder().matchTernary(
                                    PostcardConstants.HDR_LOCAL_METADATA_L4_SRC_PORT,
                                    ((TcpPortCriterion) criterion).tcpPort().toInt(), PORTMASK)
                                    .build());
                    break;
                case UDP_SRC:
                    sBuilder.matchPi(
                            PiCriterion.builder().matchTernary(
                                    PostcardConstants.HDR_LOCAL_METADATA_L4_SRC_PORT,
                                    ((UdpPortCriterion) criterion).udpPort().toInt(), PORTMASK)
                                    .build());
                    break;
                case TCP_DST:
                    sBuilder.matchPi(
                            PiCriterion.builder().matchTernary(
                                    PostcardConstants.HDR_LOCAL_METADATA_L4_DST_PORT,
                                    ((TcpPortCriterion) criterion).tcpPort().toInt(), PORTMASK)
                                    .build());
                    break;
                case UDP_DST:
                    sBuilder.matchPi(
                            PiCriterion.builder().matchTernary(
                                    PostcardConstants.HDR_LOCAL_METADATA_L4_DST_PORT,
                                    ((UdpPortCriterion) criterion).udpPort().toInt(), PORTMASK)
                                    .build());
                    break;
                default:
                    log.warn("Unsupported criterion type: {}", criterion.type());
            }
        }

            flowRules.add(
                    DefaultFlowRule.builder()
                    .forDevice(this.data().deviceId())
                    .withSelector(sBuilder.build())
                    .withTreatment(instTreatment)
                    .withPriority(DEFAULT_PRIORITY)
                    .forTable(PostcardConstants.INGRESS_PROCESS_ACTIVATE_POSTCARD_TB_POSTCARD_TELEMETRY)
                    .fromApp(appId)
                    .makePermanent()
                    .build()
            );

        return flowRules;
    }

    private int buildInstructionBitmap(Set<IntMetadataType> metadataTypes) {
        int instBitmap = 0;
        for (IntMetadataType metadataType : metadataTypes) {
            switch (metadataType) {
                /*case SWITCH_ID:
                    instBitmap |= (1 << 15);
                    break;*/
                case L1_PORT_ID:
                    instBitmap |= (1 << 14);
                    break;
                case HOP_LATENCY:
                    instBitmap |= (1 << 13);
                    break;
                case QUEUE_OCCUPANCY:
                    instBitmap |= (1 << 12);
                    break;
                case INGRESS_TIMESTAMP:
                    instBitmap |= (1 << 11);
                    break;
                case EGRESS_TIMESTAMP:
                    instBitmap |= (1 << 10);
                    break;
                case L2_PORT_ID:
                    instBitmap |= (1 << 9);
                    break;
                case EGRESS_TX_UTIL:
                    instBitmap |= (1 << 8);
                    break;
                default:
                    log.info("Unsupported metadata type {}. Ignoring...", metadataType);
                    break;
            }
        }
        return instBitmap;
    }

    /**
     * Returns a subset of Criterion from given selector, which is unsupported
     * by this INT pipeline.
     *
     * @param selector a traffic selector
     * @return a subset of Criterion from given selector, unsupported by this
     * INT pipeline, empty if all criteria are supported.
     */
    private Set<Criterion> unsupportedSelectors(TrafficSelector selector) {
        return selector.criteria().stream()
                .filter(criterion -> !SUPPORTED_CRITERION.contains(criterion.type()))
                .collect(Collectors.toSet());
    }

    private boolean processIntObjective(IntObjective obj, boolean install) {
        if (!setupBehaviour()) {
            return false;
        }
        if (install && !unsupportedSelectors(obj.selector()).isEmpty()) {
            log.warn("Device {} does not support criteria {} for INT.",
                     deviceId, unsupportedSelectors(obj.selector()));
            return false;
        }
        Set<FlowRule> flowRules = buildWatchlistEntries(obj);
        if (flowRules != null) {
            if (install) {
                flowRules.forEach(flowRule -> {
                flowRuleService.applyFlowRules(flowRule);}
                );
            } else {
                flowRules.forEach(flowRule -> {
                    flowRuleService.removeFlowRules(flowRule);
                });
            }
            log.debug("IntObjective {} has been {} {}",
                    obj, install ? "installed to" : "removed from", deviceId);
            return true;
        } else {
            log.warn("Failed to {} IntObjective {} on {}",
                    install ? "install" : "remove", obj, deviceId);
            return false;
        }
    }

    private boolean setupIntReportInternal(IntDeviceConfig cfg) {
        if (!setupBehaviour()) {
            return false;
        }

        FlowRule reportRule = buildReportEntry(cfg);
        if (reportRule != null) {
            flowRuleService.applyFlowRules(reportRule);
            log.info("Report entry {} has been added to {}", reportRule, this.data().deviceId());
            return true;
        } else {
            log.warn("Failed to add report entry on {}", this.data().deviceId());
            return false;
        }
    }

    private FlowRule buildReportEntry(IntDeviceConfig cfg) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchPi(PiCriterion.builder().matchExact(
                         PostcardConstants.HDR_REPORT_IS_VALID, (byte) 0x01)
                                 .build())
                .build();
        PiActionParam srcMacParam = new PiActionParam(
                PostcardConstants.SRC_MAC,
                ImmutableByteSequence.copyFrom(cfg.sinkMac().toBytes()));
        PiActionParam nextHopMacParam = new PiActionParam(
                PostcardConstants.MON_MAC,
                ImmutableByteSequence.copyFrom(cfg.collectorNextHopMac().toBytes()));
        PiActionParam srcIpParam = new PiActionParam(
                PostcardConstants.SRC_IP,
                ImmutableByteSequence.copyFrom(cfg.sinkIp().toOctets()));
        PiActionParam monIpParam = new PiActionParam(
                PostcardConstants.MON_IP,
                ImmutableByteSequence.copyFrom(cfg.collectorIp().toOctets()));
        PiActionParam monPortParam = new PiActionParam(
                PostcardConstants.MON_PORT,
                ImmutableByteSequence.copyFrom(cfg.collectorPort().toInt()));
        PiAction reportAction = PiAction.builder()
                .withId(PostcardConstants.EGRESS_PROCESS_POSTCARD_REPORT_DO_REPORT_ENCAPSULATION)
                .withParameter(srcMacParam)
                .withParameter(nextHopMacParam)
                .withParameter(srcIpParam)
                .withParameter(monIpParam)
                .withParameter(monPortParam)
                .build();
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .piTableAction(reportAction)
                .build();

        return DefaultFlowRule.builder()
                .withSelector(selector)
                .withTreatment(treatment)
                .fromApp(appId)
                .withPriority(DEFAULT_PRIORITY)
                .makePermanent()
                .forDevice(this.data().deviceId())
                .forTable(PostcardConstants.EGRESS_PROCESS_POSTCARD_REPORT_TB_GENERATE_REPORT)
                .build();
    }

}
