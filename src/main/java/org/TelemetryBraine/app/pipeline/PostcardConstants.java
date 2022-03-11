/*
 * Copyright 2017-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.TelemetryBraine.app.pipeline;

import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiPacketMetadataId;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;

/**
 * Constants for INT pipeline.
 */
public final class PostcardConstants {

    // hide default constructor
    private PostcardConstants() {
    }

    // Header field IDs
    public static final PiMatchFieldId HDR_STANDARD_METADATA_EGRESS_SPEC =
            PiMatchFieldId.of("standard_metadata.egress_spec");
    public static final PiMatchFieldId HDR_LOCAL_METADATA_L4_DST_PORT =
            PiMatchFieldId.of("local_metadata.l4_dst_port");
    public static final PiMatchFieldId HDR_LOCAL_METADATA_L4_SRC_PORT =
            PiMatchFieldId.of("local_metadata.l4_src_port");
    public static final PiMatchFieldId HDR_LOCAL_METADATA_L3_DST_ADDR =
            PiMatchFieldId.of("local_metadata.l3_dst_add");
    public static final PiMatchFieldId HDR_LOCAL_METADATA_L3_SRC_ADDR =
            PiMatchFieldId.of("local_metadata.l3_src_add");
    public static final PiMatchFieldId HDR_STANDARD_METADATA_INGRESS_PORT =
            PiMatchFieldId.of("standard_metadata.ingress_port");
    public static final PiMatchFieldId HDR_REPORT_IS_VALID =
            PiMatchFieldId.of("report_is_valid");
    // Table IDs
    public static final PiTableId INGRESS_PROCESS_ACTIVATE_POSTCARD_TB_POSTCARD_TELEMETRY =
            PiTableId.of("ingress.process_activate_postcard.tb_postcard_telemetry");
    public static final PiTableId EGRESS_PROCESS_POSTCARD_REPORT_TB_GENERATE_REPORT =
            PiTableId.of("egress.process_postcard_report.tb_generate_report");
    public static final PiTableId EGRESS_PROCESS_POST_META_TB_INT_INSERT =
            PiTableId.of("egress.process_post_meta.tb_int_insert");
    public static final PiTableId INGRESS_PROCESS_INT_SOURCE_SINK_TB_SET_SINK =
            PiTableId.of("ingress.process_int_source_sink.tb_set_sink");
    public static final PiTableId INGRESS_PROCESS_INT_SOURCE_SINK_TB_SET_SOURCE =
            PiTableId.of("ingress.process_int_source_sink.tb_set_source");
    // Action IDs
    public static final PiActionId INGRESS_PROCESS_ACTIVATE_POSTCARD_ACTIVATE_POSTCARD =
            PiActionId.of("ingress.process_activate_postcard.activate_postcard");
    public static final PiActionId EGRESS_PROCESS_POSTCARD_REPORT_DO_REPORT_ENCAPSULATION =
            PiActionId.of("egress.process_postcard_report.do_report_encapsulation");
    public static final PiActionId INGRESS_PROCESS_INT_SOURCE_SINK_INT_SET_SOURCE =
            PiActionId.of("ingress.process_int_source_sink.int_set_source");
    public static final PiActionId NO_ACTION = PiActionId.of("NoAction");
    public static final PiActionId INGRESS_PROCESS_INT_SOURCE_SINK_INT_SET_SINK =
            PiActionId.of("ingress.process_int_source_sink.int_set_sink");
    public static final PiActionId EGRESS_PROCESS_POST_META_INIT_METADATA =
            PiActionId.of("egress.process_post_meta.init_metadata");
    public static final PiActionId NOP = PiActionId.of("nop");
    // Action Param IDs
    public static final PiActionParamId INS_MASK0407 =
            PiActionParamId.of("instruction_mask_0407");
    public static final PiActionParamId MON_PORT =
            PiActionParamId.of("mon_port");
    public static final PiActionParamId MON_MAC = PiActionParamId.of("mon_mac");
    public static final PiActionParamId MON_IP = PiActionParamId.of("mon_ip");
    public static final PiActionParamId SWITCH_ID =
            PiActionParamId.of("switch_id");
    public static final PiActionParamId FLOW_ID =
            PiActionParamId.of("flow_id");
    public static final PiActionParamId SRC_MAC = PiActionParamId.of("src_mac");
    public static final PiActionParamId INS_MASK0003 =
            PiActionParamId.of("instruction_mask_0003");
    public static final PiActionParamId SRC_IP = PiActionParamId.of("src_ip");
}
