/* -*- P4_16 -*- */
#ifndef __POST_TRANSIT__
#define __POST_TRANSIT__
control process_post_meta (
    inout headers_t hdr,
    inout local_metadata_t local_metadata,
    inout standard_metadata_t standard_metadata) {

    action init_metadata(   switch_id_t switch_id, 
                            flow_id_t flow_id, 
                            instruction_mask_0003_t instruction_mask_0003,
                            instruction_mask_0407_t instruction_mask_0407)
    {

        local_metadata.postcard_meta.switch_id = switch_id;
        local_metadata.postcard_meta.flow_id = flow_id;
        local_metadata.postcard_meta.instruction_mask_0003 = instruction_mask_0003;
        local_metadata.postcard_meta.instruction_mask_0407 = instruction_mask_0407;
    }
    // Default action used to set switch ID.
    table tb_int_insert {
        // We don't really need a key here, however we add a dummy one as a
        // workaround to ONOS inability to properly support default actions.
        key = {
            IS_I2E_CLONE(standard_metadata) : exact @name("report_is_valid");
            hdr.ipv4.src_addr: ternary;
            hdr.ipv4.dst_addr: ternary;
        }
        actions = {
            init_metadata;
            @defaultonly nop;
        }
        const default_action = nop();
    }

    apply {
        tb_int_insert.apply();
    }
}

#endif
