/* -*- P4_16 -*- */
#ifndef __INT_TRANSIT__
#define __INT_TRANSIT__
control process_int_transit (
    inout headers_t hdr,
    inout local_metadata_t local_metadata,
    inout standard_metadata_t standard_metadata) {

    action init_metadata(switch_id_t switch_id, flow_id_t flow_id) {
        local_metadata.postcard_meta.switch_id = switch_id;
        local_metadata.postcard_meta.flow_id = flow_id;
    }
    // Default action used to set switch ID.
    table tb_int_insert {
        // We don't really need a key here, however we add a dummy one as a
        // workaround to ONOS inability to properly support default actions.
        key = {
            IS_I2E_CLONE(standard_metadata) : exact @name("report_is_valid");
        }
        actions = {
            init_metadata;
            @defaultonly nop;
        }
        const default_action = nop();
        size = 1;
    }

    apply {
        tb_int_insert.apply();
    }
}

#endif
