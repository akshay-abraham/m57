// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import com.lyra.blocker.family.DnsPacket;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class DnsPacketChecks {
    public static void main(String[] args) throws Exception { run(); System.out.println("DNS packet checks passed"); }
    public static void run() throws Exception {
        for (boolean ipv6 : new boolean[]{false,true}) {
            byte[] wire = request(ipv6);
            DnsPacket packet = DnsPacket.parse(wire, wire.length);
            check(packet != null && packet.host.equals("blocked.example"), "DNS query parse failed");
            int header = ipv6 ? 40 : 20;
            byte[] denied = packet.denied();
            check(u16(denied,header)==53 && u16(denied,header+2)==43000, "UDP ports not reversed");
            check(u16(denied,header+8)==0x1234, "DNS request ID changed");
            check((denied[header+11]&15)==3 && (denied[header+10]&0x80)!=0, "Blocked response is not NXDOMAIN");
            check(u16(denied,header+14)==0 && u16(denied,header+16)==0, "Error retained answer counts");
            if (!ipv6) check(sum(denied,0,20)==0xffff, "IPv4 header checksum invalid");
            long pseudo = 17 + denied.length-header + sum(denied,ipv6?8:12,ipv6?32:8);
            check(fold(pseudo+sum(denied,header,denied.length-header))==0xffff, "UDP checksum invalid");
            byte[] response = packet.query.clone(); response[2]=(byte)0x81;response[3]=(byte)0x80;
            check((packet.answer(response)[header+11]&15)==0, "Valid upstream answer rejected");
            byte[] wrongOpcode=response.clone(); wrongOpcode[2]|=0x08;
            check((packet.answer(wrongOpcode)[header+11]&15)==2, "Mismatched DNS operation accepted");
            byte[] wrongQuestion=response.clone(); wrongQuestion[wrongQuestion.length-3]=28;
            check((packet.answer(wrongQuestion)[header+11]&15)==2, "Mismatched DNS question type accepted");
            response[0]^=1;
            check((packet.answer(response)[header+11]&15)==2, "Mismatched upstream ID accepted");
            byte[] badLength=wire.clone(); badLength[header+5]--;
            check(DnsPacket.parse(badLength,badLength.length)==null, "Malformed UDP length accepted");
            byte[] badName=wire.clone(); badName[header+20]=(byte)0xc0;badName[header+21]=12;
            check(DnsPacket.parse(badName,badName.length)==null, "Compression cycle accepted");
            check(DnsPacket.parse(wire,10)==null, "Truncated IP packet accepted");
            for (int type : new int[]{28,65}) {
                byte[] typed=wire.clone(); typed[typed.length-3]=(byte)type;
                DnsPacket other=DnsPacket.parse(typed,typed.length);
                check(other!=null && (other.denied()[header+11]&15)==3,"AAAA/HTTPS query did not block");
            }
        }
        byte[] original=request(false);
        byte[] options=new byte[original.length+20];
        System.arraycopy(original,0,options,0,20);
        System.arraycopy(original,20,options,40,original.length-20);
        options[0]=0x4a;put16(options,2,options.length);
        DnsPacket withOptions=DnsPacket.parse(options,options.length);
        check(withOptions!=null,"IPv4 options query was rejected");
        byte[] optionReply=withOptions.denied();
        check(sum(optionReply,0,40)==0xffff,"IPv4 options checksum invalid");
        check(fold(17+optionReply.length-40+sum(optionReply,12,8)+sum(optionReply,40,optionReply.length-40))==0xffff,"IPv4 options UDP checksum invalid");
        byte[] fragment=request(false); fragment[6]=0x20;
        check(DnsPacket.parse(fragment,fragment.length)==null, "Fragmented request accepted without reassembly");
    }
    private static byte[] request(boolean v6) throws Exception {
        ByteArrayOutputStream dns=new ByteArrayOutputStream();
        dns.write(new byte[]{0x12,0x34,1,0,0,1,0,0,0,0,0,0});
        for(String part:"blocked.example".split("\\.")) { dns.write(part.length());dns.write(part.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); }
        dns.write(new byte[]{0,0,1,0,1});
        int header=v6?40:20;
        byte[] packet=new byte[header+8+dns.size()];
        if(v6) {
            packet[0]=0x60;put16(packet,4,packet.length-40);packet[6]=17;packet[7]=64;
            packet[8]=(byte)0xfd;packet[9]=0x57;packet[23]=1;
            packet[24]=(byte)0xfd;packet[25]=0x57;packet[39]=2;
        } else {
            packet[0]=0x45;put16(packet,2,packet.length);packet[8]=64;packet[9]=17;
            packet[12]=10;packet[13]=57;packet[15]=1;packet[16]=10;packet[17]=57;packet[19]=2;
        }
        put16(packet,header,43000);put16(packet,header+2,53);put16(packet,header+4,8+dns.size());
        System.arraycopy(dns.toByteArray(),0,packet,header+8,dns.size());return packet;
    }
    private static int u16(byte[] b,int i){return ((b[i]&255)<<8)|(b[i+1]&255);}
    private static void put16(byte[]b,int i,int v){b[i]=(byte)(v>>>8);b[i+1]=(byte)v;}
    private static long sum(byte[]b,int start,int length){long s=0;for(int i=0;i<length;i+=2)s+=((b[start+i]&255)<<8)|(i+1<length?b[start+i+1]&255:0);return fold(s);}
    private static long fold(long n){while((n>>>16)!=0)n=(n&65535)+(n>>>16);return n;}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
