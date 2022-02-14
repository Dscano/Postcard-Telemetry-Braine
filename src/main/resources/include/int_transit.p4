#ifndef __INT_TRANSIT__
#define __INT_TRANSIT__
control process_int_transit (
    inout headers_t hdr,
    inout local_metadata_t local_metadata,
    inout standard_metadata_t standard_metadata) {

    @hidden
    action int_set_header_1() { //level1_port_id
        hdr.report_fixed_header.ingress_port_id = (bit<16>) standard_metadata.ingress_port;
        hdr.report_fixed_header.egress_port_id = (bit<16>) standard_metadata.egress_port;
    }
    @hidden
    action int_set_header_2() { //hop_latency
        hdr.report_fixed_header.hop_latency = (bit<32>) standard_metadata.egress_global_timestamp - (bit<32>) standard_metadata.ingress_global_timestamp;
    }
    @hidden
    action int_set_header_3() { //q_occupancy
        // TODO: Support egress queue ID
        hdr.report_fixed_header.q_id = 0;
        hdr.report_fixed_header.q_occupancy =(bit<24>) standard_metadata.deq_qdepth;
    }
    @hidden
    action int_set_header_4() { //ingress_tstamp
        hdr.report_fixed_header.ingress_tstamp = (bit<32>) standard_metadata.ingress_global_timestamp;
    }
    @hidden
    action int_set_header_5() { //egress_timestamp
        hdr.report_fixed_header.egress_tstamp = (bit<32>) standard_metadata.egress_global_timestamp;
    }
    @hidden
    action int_set_header_6() { //level2_port_id
        hdr.report_fixed_header.ingress_port_id = (bit<16>) standard_metadata.ingress_port;
        hdr.report_fixed_header.egress_port_id = (bit<16>) standard_metadata.egress_port;
     }
    @hidden
    action int_set_header_7() { //egress_port_tx_utilization
        hdr.report_fixed_header.egress_port_tx_util = 0;
    }

     /* action function for bits 0-3 combinations, 0 is msb, 3 is lsb */
     /* Each bit set indicates that corresponding INT header should be added */
    @hidden
     action int_set_header_0003_i0() {
     }
    @hidden
     action int_set_header_0003_i1() {
        int_set_header_3();
    }
    @hidden
    action int_set_header_0003_i2() {
        int_set_header_2();
    }
    @hidden
    action int_set_header_0003_i3() {
        int_set_header_3();
        int_set_header_2();
    }
    @hidden
    action int_set_header_0003_i4() {
        int_set_header_1();
    }
    @hidden
    action int_set_header_0003_i5() {
        int_set_header_3();
        int_set_header_1();
    }
    @hidden
    action int_set_header_0003_i6() {
        int_set_header_2();
        int_set_header_1();
    }
    @hidden
    action int_set_header_0003_i7() {
        int_set_header_3();
        int_set_header_2();
        int_set_header_1();
    }
    @hidden
    action int_set_header_0003_i8() {
    }
    @hidden
    action int_set_header_0003_i9() {
        int_set_header_3();
    }
    @hidden
    action int_set_header_0003_i10() {
        int_set_header_2();
    }
    @hidden
    action int_set_header_0003_i11() {
        int_set_header_3();
        int_set_header_2();
    }
    @hidden
    action int_set_header_0003_i12() {
        int_set_header_1();
    }
    @hidden
    action int_set_header_0003_i13() {
        int_set_header_3();
        int_set_header_1();
    }
    @hidden
    action int_set_header_0003_i14() {
        int_set_header_2();
        int_set_header_1();
    }
    @hidden
    action int_set_header_0003_i15() {
        int_set_header_3();
        int_set_header_2();
        int_set_header_1();
    }

     /* action function for bits 4-7 combinations, 4 is msb, 7 is lsb */
    @hidden
    action int_set_header_0407_i0() {

    }
    @hidden
    action int_set_header_0407_i1() {
        int_set_header_7();
    }
    @hidden
    action int_set_header_0407_i2() {
        int_set_header_6();
    }
    @hidden
    action int_set_header_0407_i3() {
        int_set_header_7();
        int_set_header_6();
    }
    @hidden
    action int_set_header_0407_i4() {
        int_set_header_5();
    }
    @hidden
    action int_set_header_0407_i5() {
        int_set_header_7();
        int_set_header_5();
    }
    @hidden
    action int_set_header_0407_i6() {
        int_set_header_6();
        int_set_header_5();
    }
    @hidden
    action int_set_header_0407_i7() {
        int_set_header_7();
        int_set_header_6();
        int_set_header_5();
    }
    @hidden
    action int_set_header_0407_i8() {
        int_set_header_4();
    }
    @hidden
    action int_set_header_0407_i9() {
        int_set_header_7();
        int_set_header_4();
    }
    @hidden
    action int_set_header_0407_i10() {
        int_set_header_6();
        int_set_header_4();
    }
    @hidden
    action int_set_header_0407_i11() {
        int_set_header_7();
        int_set_header_6();
        int_set_header_4();
    }
    @hidden
    action int_set_header_0407_i12() {
        int_set_header_5();
        int_set_header_4();
    }
    @hidden
    action int_set_header_0407_i13() {
        int_set_header_7();
        int_set_header_5();
        int_set_header_4();
    }
    @hidden
    action int_set_header_0407_i14() {
        int_set_header_6();
        int_set_header_5();
        int_set_header_4();
    }
    @hidden
    action int_set_header_0407_i15() {
        int_set_header_7();
        int_set_header_6();
        int_set_header_5();
        int_set_header_4();
    }

    /* Table to process instruction bits 0-3 */
    @hidden
    table tb_int_inst_0003 {
        key = {
            local_metadata.postcard_meta.instruction_mask_0003 : exact;
        }
        actions = {
            int_set_header_0003_i0;
            int_set_header_0003_i1;
            int_set_header_0003_i2;
            int_set_header_0003_i3;
            int_set_header_0003_i4;
            int_set_header_0003_i5;
            int_set_header_0003_i6;
            int_set_header_0003_i7;
            int_set_header_0003_i8;
            int_set_header_0003_i9;
            int_set_header_0003_i10;
            int_set_header_0003_i11;
            int_set_header_0003_i12;
            int_set_header_0003_i13;
            int_set_header_0003_i14;
            int_set_header_0003_i15;
        }
        const entries = {
            (0x0) : int_set_header_0003_i0();
            (0x1) : int_set_header_0003_i1();
            (0x2) : int_set_header_0003_i2();
            (0x3) : int_set_header_0003_i3();
            (0x4) : int_set_header_0003_i4();
            (0x5) : int_set_header_0003_i5();
            (0x6) : int_set_header_0003_i6();
            (0x7) : int_set_header_0003_i7();
            (0x8) : int_set_header_0003_i8();
            (0x9) : int_set_header_0003_i9();
            (0xA) : int_set_header_0003_i10();
            (0xB) : int_set_header_0003_i11();
            (0xC) : int_set_header_0003_i12();
            (0xD) : int_set_header_0003_i13();
            (0xE) : int_set_header_0003_i14();
            (0xF) : int_set_header_0003_i15();
        }
    }

    /* Table to process instruction bits 4-7 */
    @hidden
    table tb_int_inst_0407 {
        key = {
            local_metadata.postcard_meta.instruction_mask_0407 : exact;
        }
        actions = {
            int_set_header_0407_i0;
            int_set_header_0407_i1;
            int_set_header_0407_i2;
            int_set_header_0407_i3;
            int_set_header_0407_i4;
            int_set_header_0407_i5;
            int_set_header_0407_i6;
            int_set_header_0407_i7;
            int_set_header_0407_i8;
            int_set_header_0407_i9;
            int_set_header_0407_i10;
            int_set_header_0407_i11;
            int_set_header_0407_i12;
            int_set_header_0407_i13;
            int_set_header_0407_i14;
            int_set_header_0407_i15;
        }
        const entries = {
            (0x0) : int_set_header_0407_i0();
            (0x1) : int_set_header_0407_i1();
            (0x2) : int_set_header_0407_i2();
            (0x3) : int_set_header_0407_i3();
            (0x4) : int_set_header_0407_i4();
            (0x5) : int_set_header_0407_i5();
            (0x6) : int_set_header_0407_i6();
            (0x7) : int_set_header_0407_i7();
            (0x8) : int_set_header_0407_i8();
            (0x9) : int_set_header_0407_i9();
            (0xA) : int_set_header_0407_i10();
            (0xB) : int_set_header_0407_i11();
            (0xC) : int_set_header_0407_i12();
            (0xD) : int_set_header_0407_i13();
            (0xE) : int_set_header_0407_i14();
            (0xF) : int_set_header_0407_i15();
        }
    }

    apply {
        tb_int_inst_0003.apply();
        tb_int_inst_0407.apply();
    }
}

#endif