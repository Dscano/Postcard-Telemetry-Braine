#sudo python Parsing_INT_pkt.py enp4s0 udp 54321 1000 0
#sudo python Parsing_INT_pkt.py enp4s1 udp 54321 1000 0

#print int(binascii.b2a_hex(str(l)[4:8]), 16)  # switchid
#print int(binascii.b2a_hex(str(l)[12:16]), 16)  # flowid
#print int(binascii.b2a_hex(str(l)[16:20]), 16)  # hoplatency



import binascii
from scapy.all import *
import sys
import keyboard
import os
import datetime
import influxdb
import time

dicts = {}

#{'switchID': 0, 'flowID': 0,'hop_latency_avg': 0, 'hop_latency_max': 0, 'num': 0}
hop_latency_1 = 0
hop_latency_2 = 0
hop_latency_1_max = 0
hop_latency_2_max = 0
switch_id = None
n1 = 0
n2 = 0

#grafana push period
period=0.5

toDB=1
ipdb='172.30.200.40'
db="Postcard-P4-telemetry"
client=None
drop=1

#ipdb='localhost'
epoch = datetime.datetime(1970,1,1)
polling=1

def send_data():
  global dicts
  for f_id, data in dicts.items():
      fields={"hop_latency_avg": ((data['hop_latency_avg']/data['num'])*1000), "hop_latency_max": data['hop_latency_max'] *1000,
              "switchID": data['switchID'], "flowID": f_id}
      print(fields)
      d = datetime.datetime.utcnow()
      ts = int(((d - epoch).total_seconds())*1000000000)
      json = [{'measurement': 'measurement',
                # 'tags': {"switchID": switch_id ,"serviceID": 1 },
                 'time': ts,
                 'fields': fields }]
      #print fields
      client.write_points(json, time_precision='n')
      #time.sleep(polling)


def pkt_callback(pkt):
    global dicts

    l=pkt[Raw]
    if(int(binascii.b2a_hex(str(l)[12:16]),16) in dicts):
        dicts[int(binascii.b2a_hex(str(l)[12:16]),16)]['hop_latency_avg'] += int(binascii.b2a_hex(str(l)[16:20]), 16)
        if( dicts[int(binascii.b2a_hex(str(l)[12:16]),16)]['hop_latency_max'] > int(binascii.b2a_hex(str(l)[16:20]), 16)):
            dicts[int(binascii.b2a_hex(str(l)[12:16]), 16)]['hop_latency_max'] = int(binascii.b2a_hex(str(l)[16:20]), 16)
        dicts[int(binascii.b2a_hex(str(l)[12:16]), 16)]['num'] += 1
        print(dicts)
    else:
        dicts.update({int(binascii.b2a_hex(str(l)[12:16]),16):
                      {'switchID': int(binascii.b2a_hex(str(l)[4:8]), 16),
                      'hop_latency_avg': int(binascii.b2a_hex(str(l)[16:20]), 16),
                      'hop_latency_max': int(binascii.b2a_hex(str(l)[16:20]), 16),
                      'num': 1}})
        print(dicts)

def main():
    global client
    global toDB
    global ipdb
    global db
    global drop
    global period

    try:  
        interface = str(sys.argv[1])
        protocol = str(sys.argv[2])
        port_selected = str(sys.argv[3])
        rate = int(sys.argv[4])
        cycle = int(sys.argv[5])
        filter= protocol +" port " + port_selected
        if toDB:
             client=influxdb.InfluxDBClient(ipdb, 8086, 'root', 'root', db)
             if interface == "eth4":
                if drop:
                   client.drop_database(db)
                client.create_database(db)
        n = 1
        N = int(rate) * period
        print "Sniffing packets at port: "+ interface + " at " + protocol +" port: "+ port_selected +" numbers of pkts: " + str(N) + " cycles: "+ str(cycle)
    except:
        print "Usage: sudo python Parsing_INT_pkt.py <name_inteface> <protocol> <udp/tcp port> <n packets> <n cycle>"
        sys.exit(1)
    while True:
      try:
        sniff(iface = interface, prn=pkt_callback, filter = filter, store = 0, count = int(N))
        if toDB:
            send_data()
        dicts.clear()
        #print str(hop_latency_1_max) , str(hop_latency_2_max), str(hop_latency_1), str(hop_latency_2)
        #print "cycle", n
        if n == cycle and n != 0:
          print 'Interrupted! cycle number', n
          sys.exit(1)
        n = n+1
      except KeyboardInterrupt:
          print 'interrupted!'
          sys.exit(1)
            
                        

main()
