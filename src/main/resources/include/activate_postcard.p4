#ifndef __ACTIVATE_POSTCARD__
#define __ACTIVATE_POSTCARD__

// Activate Postcard Telemetry to the packet
control process_activate_postcard (
    inout headers_t hdr,
    inout local_metadata_t local_metadata,
    inout standard_metadata_t standard_metadata) {

    direct_counter(CounterType.packets_and_bytes) counter_int_source;
  

    action activate_postcard() {
        local_metadata.postcard_meta.activate_postcard = _TRUE;
        counter_int_source.count();
    }

    table tb_postcard_telemetry{
        key = {
            local_metadata.l3_src_add : ternary;
            local_metadata.l3_dst_add : ternary;
            local_metadata.l4_src_port: ternary; 
            local_metadata.l4_dst_port: ternary;
        }
        actions = {
            activate_postcard;
            @defaultonly nop();
        }
        counters = counter_int_source;
        const default_action = nop();
    }

    apply {
        tb_postcard_telemetry.apply();
    }
}

#endif
