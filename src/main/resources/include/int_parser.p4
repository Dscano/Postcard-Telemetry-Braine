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

/* -*- P4_16 -*- */
#ifndef __INT_PARSER__
#define __INT_PARSER__

parser int_parser (
    packet_in packet,
    out headers_t hdr,
    inout local_metadata_t local_metadata,
    inout standard_metadata_t standard_metadata) {
    state start {
        transition select(standard_metadata.ingress_port) {
            CPU_PORT: parse_packet_out;
            default: parse_ethernet;
        }
    }

    state parse_packet_out {
        packet.extract(hdr.packet_out);
        transition parse_ethernet;
    }

    state parse_ethernet {
        packet.extract(hdr.ethernet);
        local_metadata.l2_src_add = hdr.ethernet.src_addr;
        local_metadata.l2_dst_add = hdr.ethernet.dst_addr;
        transition select(hdr.ethernet.ether_type) {
            ETH_TYPE_IPV4 : parse_ipv4;
            default : accept;
        }
    }

    state parse_ipv4 {
        packet.extract(hdr.ipv4);
        local_metadata.l3_src_add = hdr.ipv4.src_addr;
        local_metadata.l3_dst_add = hdr.ipv4.dst_addr;
        transition select(hdr.ipv4.protocol) {
            IP_PROTO_TCP : parse_tcp;
            IP_PROTO_UDP : parse_udp;
            default: accept;
        }
    }

    state parse_tcp {
        packet.extract(hdr.tcp);
        local_metadata.l4_src_port = hdr.tcp.src_port;
        local_metadata.l4_dst_port = hdr.tcp.dst_port;
        transition accept;
    }

    state parse_udp {
        packet.extract(hdr.udp);
        local_metadata.l4_src_port = hdr.udp.src_port;
        local_metadata.l4_dst_port = hdr.udp.dst_port;
        transition select(hdr.udp.dst_port) {
            VXLAN_UDP_PORT: parse_vxlan;
            default: accept;
        }
    }

    state parse_vxlan {
        packet.extract(hdr.vxlan);
        transition parse_ethernet_inner;
    }

    state parse_ethernet_inner {
        packet.extract(hdr.ethernet_inner);
        local_metadata.l2_src_add = hdr.ethernet_inner.src_addr;
        local_metadata.l2_dst_add = hdr.ethernet_inner.dst_addr;
        transition select(hdr.ethernet_inner.ether_type) {
            ETH_TYPE_IPV4 : parse_ipv4_inner;
            default : accept;
        }
    }

    state parse_ipv4_inner{
        packet.extract(hdr.ipv4_inner);
        local_metadata.l3_src_add = hdr.ipv4_inner.src_addr;
        local_metadata.l3_dst_add = hdr.ipv4_inner.dst_addr;
        transition select(hdr.ipv4_inner.protocol) {
            IP_PROTO_TCP : parse_tcp_inner;
            IP_PROTO_UDP : parse_udp_inner;
            default: accept;
        }
    }

    state parse_tcp_inner {
        packet.extract(hdr.tcp_inner);
        transition accept;
    }

    state parse_udp_inner {
        packet.extract(hdr.udp_inner);
        transition accept;
        }

}

control int_deparser(
    packet_out packet,
    in headers_t hdr) {
    apply {
        packet.emit(hdr.packet_in);
        packet.emit(hdr.report_ethernet);
        packet.emit(hdr.report_ipv4);
        packet.emit(hdr.report_udp);
        packet.emit(hdr.report_fixed_header);
        packet.emit(hdr.ethernet);
        packet.emit(hdr.ipv4);
        packet.emit(hdr.tcp);
        packet.emit(hdr.udp);
        packet.emit(hdr.vxlan);
        packet.emit(hdr.ethernet_inner);
        packet.emit(hdr.ipv4_inner);
        packet.emit(hdr.udp_inner);
        packet.emit(hdr.tcp_inner);
    }
}

#endif
