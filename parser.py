#sudo python Parsing_INT_pkt.py eth1 udp 54321 1000 0
#sudo python Parsing_INT_pkt.py eth2 udp 54321 1000 0


import binascii
from scapy.all import *
import sys
import keyboard
import os



def pkt_callback(pkt):
    l=pkt[Raw]
    print int(binascii.b2a_hex(str(l)[4:8]),16) #switchid
    print int(binascii.b2a_hex(str(l)[12:16]),16) #switchid
    print int(binascii.b2a_hex(str(l)[16:20]),16) #hoplatency
   

def main():  
    try:  
        interface = str(sys.argv[1])
        protocol =  str(sys.argv[2])
        port_selected = str(sys.argv[3])
        filter= protocol +" port " + port_selected
        print "Sniffing packets at port: "+ interface + " at " + protocol +" port: "+ port_selected
    except:
        print "Usage: sudo python Parsing_INT_pkt.py <name_inteface> <protocol> <udp/tcp port> <n packets> <n cycle>"
        sys.exit(1)
    while True:
      try:
        sniff(iface = interface, prn=pkt_callback, filter = filter)
      except KeyboardInterrupt:
          print 'interrupted!'
          sys.exit(1)
main()