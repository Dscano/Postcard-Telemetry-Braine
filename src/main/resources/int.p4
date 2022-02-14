#define TARGET_BMV2

#include <core.p4>
#include <v1model.p4>

#include "include/defines.p4"
#include "include/headers.p4"
#include "include/actions.p4"
#include "include/packet_io.p4"
#include "include/port_counters.p4"
#include "include/table0.p4"
#include "include/checksums.p4"
#include "include/int_parser.p4"
#include "include/activate_postcard.p4"
#include "include/post_transit.p4"
#include "include/int_transit.p4"
#include "include/postcard_report.p4"


control ingress (
    inout headers_t hdr,
    inout local_metadata_t local_metadata,
    inout standard_metadata_t standard_metadata) {

    apply {
        port_counters_ingress.apply(hdr, standard_metadata);
        packetio_ingress.apply(hdr, standard_metadata);
        table0_control.apply(hdr, local_metadata, standard_metadata);
        process_activate_postcard.apply(hdr, local_metadata, standard_metadata);

        if (local_metadata.postcard_meta.activate_postcard == _TRUE) {
            // clone packet for Postcard Telemetry 
            #ifdef TARGET_BMV2
            clone3(CloneType.I2E, REPORT_MIRROR_SESSION_ID, standard_metadata);
            #endif // TARGET_BMV2
        }
    }
}

control egress (
    inout headers_t hdr,
    inout local_metadata_t local_metadata,
    inout standard_metadata_t standard_metadata) {

    apply {
        
            #ifdef TARGET_BMV2
            if (IS_I2E_CLONE(standard_metadata)) {
                /* send postcard report */
                process_post_meta.apply(hdr, local_metadata, standard_metadata);
                process_postcard_report.apply(hdr, local_metadata, standard_metadata);
                process_int_transit.apply(hdr, local_metadata, standard_metadata);
            }
        	#endif // TARGET_BMV2
        port_counters_egress.apply(hdr, standard_metadata);
        packetio_egress.apply(hdr, standard_metadata);
    }
}

V1Switch(
    int_parser(),
    verify_checksum_control(),
    ingress(),
    egress(),
    compute_checksum_control(),
    int_deparser()
) main;
