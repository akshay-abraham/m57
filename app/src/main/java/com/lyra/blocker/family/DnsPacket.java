// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker.family;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Bounded IPv4/IPv6 UDP DNS codec. No socket operations and no browsing history. */
public final class DnsPacket {
    public final byte[] query;
    public final String host;
    private final byte[] ip;
    private final int headerSize;
    private final int questionEnd;
    private final int type;
    private final int nsClass;

    private DnsPacket(byte[] ip, int headerSize, byte[] query, Question question) {
        this.ip = ip; this.headerSize = headerSize; this.query = query;
        this.host = question.host; this.questionEnd = question.end;
        this.type = question.type; this.nsClass = question.nsClass;
    }

    public static DnsPacket parse(byte[] packet, int length) {
        if (length < 28 || length > packet.length) return null;
        int version = (packet[0] & 0xff) >>> 4;
        int header;
        if (version == 4) {
            header = (packet[0] & 15) * 4;
            if (header < 20 || header + 8 > length || u16(packet, 2) != length || (packet[9] & 255) != 17 || (u16(packet, 6) & 0x3fff) != 0) return null;
        } else if (version == 6) {
            header = 40;
            if (length < 48 || u16(packet, 4) + 40 != length || (packet[6] & 255) != 17) return null;
        } else return null;
        int udpLength = u16(packet, header + 4);
        if (u16(packet, header + 2) != 53 || udpLength < 20 || header + udpLength != length || udpLength > 8192) return null;
        byte[] query = Arrays.copyOfRange(packet, header + 8, length);
        if ((query[2] & 0xf8) != 0 || u16(query, 4) != 1) return null;
        Question question = question(query);
        if (question == null) return null;
        return new DnsPacket(Arrays.copyOf(packet, header + 8), header, query, question);
    }

    public byte[] denied() { return error(3); }
    public byte[] failed() { return error(2); }
    public byte[] error(int rcode) {
        byte[] answer = Arrays.copyOf(query, questionEnd);
        answer[2] = (byte) (0x80 | (query[2] & 1));
        answer[3] = (byte) (0x80 | (rcode & 15));
        Arrays.fill(answer, 6, 12, (byte) 0);
        return wrap(answer);
    }

    public byte[] answer(byte[] response) {
        if (response == null || response.length < 12 || response.length > 65000 || (response[2] & 0x80) == 0 ||
            u16(response, 0) != u16(query, 0) || u16(response, 4) != 1) return failed();
        Question q = question(response);
        if (q == null || !q.host.equals(host) || q.type != type || q.nsClass != nsClass) return failed();
        return wrap(response);
    }

    private byte[] wrap(byte[] dns) {
        int udp = headerSize;
        byte[] result = Arrays.copyOf(ip, headerSize + 8 + dns.length);
        if ((result[0] & 0xf0) == 0x40) {
            swap(result, 12, 16, 4); put16(result, 2, result.length);
            result[8] = 64; result[10] = 0; result[11] = 0;
            put16(result, 10, checksum(result, 0, headerSize, 0));
        } else {
            swap(result, 8, 24, 16); put16(result, 4, 8 + dns.length); result[7] = 64;
        }
        swap(result, udp, udp + 2, 2); put16(result, udp + 4, 8 + dns.length);
        result[udp + 6] = 0; result[udp + 7] = 0;
        System.arraycopy(dns, 0, result, udp + 8, dns.length);
        long pseudo = 17 + 8 + dns.length;
        boolean v6 = (result[0] & 0xf0) == 0x60;
        int addressStart = v6 ? 8 : 12;
        int addressLength = v6 ? 32 : 8;
        pseudo += sum(result, addressStart, addressLength);
        int check = checksum(result, udp, result.length - udp, pseudo);
        put16(result, udp + 6, check == 0 ? 0xffff : check);
        return result;
    }

    private static final class Question {
        String host; int end; int type; int nsClass;
    }
    private static Question question(byte[] dns) {
        if (dns.length < 17) return null;
        StringBuilder host = new StringBuilder();
        int cursor = 12, end = -1, hops = 0;
        while (cursor < dns.length && hops++ < 128) {
            int size = dns[cursor++] & 255;
            if (size == 0) {
                if (end < 0) end = cursor;
                if (end + 4 > dns.length) return null;
                Question q = new Question();
                q.host = host.toString().toLowerCase(java.util.Locale.ROOT);
                q.end = end + 4; q.type = u16(dns, end); q.nsClass = u16(dns, end + 2);
                return q;
            }
            if ((size & 0xc0) == 0xc0) {
                if (cursor >= dns.length) return null;
                int pointer = ((size & 63) << 8) | (dns[cursor++] & 255);
                if (pointer >= cursor - 2 || pointer < 12) return null;
                if (end < 0) end = cursor;
                cursor = pointer;
                continue;
            }
            if (size > 63 || cursor + size > dns.length || host.length() + size + 1 > 254) return null;
            for (int i = 0; i < size; i++) if ((dns[cursor + i] & 255) > 127 || dns[cursor + i] <= 32) return null;
            if (host.length() > 0) host.append('.');
            host.append(new String(dns, cursor, size, StandardCharsets.US_ASCII)); cursor += size;
        }
        return null;
    }
    private static void swap(byte[] data, int a, int b, int size) { for (int i=0;i<size;i++) { byte item=data[a+i];data[a+i]=data[b+i];data[b+i]=item; } }
    private static int u16(byte[] data, int i) { return ((data[i]&255)<<8)|(data[i+1]&255); }
    private static void put16(byte[] data, int i, int value) { data[i]=(byte)(value>>>8);data[i+1]=(byte)value; }
    private static long sum(byte[] data, int start, int length) {
        long total=0; for(int i=0;i<length;i+=2) total+=((data[start+i]&255)<<8)|(i+1<length?data[start+i+1]&255:0); return total;
    }
    private static int checksum(byte[] data, int start, int length, long initial) {
        long total=initial+sum(data,start,length); while((total>>>16)!=0)total=(total&0xffff)+(total>>>16);return (int)(~total)&0xffff;
    }
}
