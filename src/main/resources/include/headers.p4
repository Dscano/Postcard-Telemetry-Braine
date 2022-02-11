#ifndef __HEADERS__
#define __HEADERS__

#include "defines.p4"
#include "telemetry_report_headers.p4"

@controller_header("packet_in")
header packet_in_header_t {
    bit<9> ingress_port;
    bit<7> _padding;
}

@controller_header("packet_out")
header packet_out_header_t {
    bit<9> egress_port;
    bit<7> _padding;
}

header ethernet_t {
    bit<48> dst_addr;
    bit<48> src_addr;
    bit<16> ether_type;
}
const bit<8> ETH_HEADER_LEN = 14;

header ipv4_t {
    bit<4>  version;
    bit<4>  ihl;
    bit<6>  dscp;
    bit<2>  ecn;
    bit<16> len;
    bit<16> identification;
    bit<3>  flags;
    bit<13> frag_offset;
    bit<8>  ttl;
    bit<8>  protocol;
    bit<16> hdr_checksum;
    bit<32> src_addr;
    bit<32> dst_addr;
}
const bit<8> IPV4_MIN_HEAD_LEN = 20;

header tcp_t {
    bit<16> src_port;
    bit<16> dst_port;
    bit<32> seq_no;
    bit<32> ack_no;
    bit<4>  data_offset;
    bit<3>  res;
    bit<3>  ecn;
    bit<6>  ctrl;
    bit<16> window;
    bit<16> checksum;
    bit<16> urgent_ptr;
}

header udp_t {
    bit<16> src_port;
    bit<16> dst_port;
    bit<16> length_;
    bit<16> checksum;
}
const bit<8> UDP_HEADER_LEN = 8;

struct postcard_metadata_t {
    switch_id_t switch_id;
    flow_id_t flow_id;
    bit<16> new_bytes;
    bit<8>  new_words;
    _BOOL  activate_postcard;
}

struct headers_t {
    packet_out_header_t packet_out;
    packet_in_header_t packet_in;
    // Postcard Telemetry Report Encapsulation
    ethernet_t report_ethernet;
    ipv4_t report_ipv4;
    udp_t report_udp;
    // INT Report Headers
    report_fixed_header_t report_fixed_header;
    // Original packet's headers
    ethernet_t ethernet;
    ipv4_t ipv4;
    tcp_t tcp;
    udp_t udp;

}

struct local_metadata_t {
    bit<16>       l4_src_port;
    bit<16>       l4_dst_port;
    next_hop_id_t next_hop_id;
    bit<16>       selector;
    postcard_metadata_t postcard_meta;
    bool compute_checksum;
}

#endif
