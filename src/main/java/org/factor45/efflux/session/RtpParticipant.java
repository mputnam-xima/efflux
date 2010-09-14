/*
 * Copyright 2010 Bruno de Carvalho
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

package org.factor45.efflux.session;

import org.factor45.efflux.packet.DataPacket;
import org.factor45.efflux.packet.SdesChunk;
import org.factor45.efflux.packet.SdesChunkItem;
import org.factor45.efflux.packet.SdesChunkPrivItem;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Random;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class RtpParticipant {

    // constants ------------------------------------------------------------------------------------------------------

    private static final Random RANDOM = new Random(System.nanoTime());

    // internal vars --------------------------------------------------------------------------------------------------

    private SocketAddress dataAddress;
    private SocketAddress controlAddress;
    private long ssrc;
    private String name;
    private String cname;
    private String email;
    private String phone;
    private String location;
    private String tool;
    private String note;
    private String privPrefix;
    private String priv;

    // constructors ---------------------------------------------------------------------------------------------------

    public RtpParticipant(String host, int dataPort, int controlPort, long ssrc) {
        if ((ssrc < 0) || (ssrc > 0xffffffffL)) {
            throw new IllegalArgumentException("Valid range for SSRC is [0;0xffffffff]");
        }
        if ((dataPort < 0) || (dataPort > 65536)) {
            throw new IllegalArgumentException("Invalid port number; use range [0;65536]");
        }
        if ((controlPort < 0) || (controlPort > 65536)) {
            throw new IllegalArgumentException("Invalid port number; use range [0;65536]");
        }
        this.dataAddress = new InetSocketAddress(host, dataPort);
        this.controlAddress = new InetSocketAddress(host, controlPort);
        this.ssrc = ssrc;
    }

    public RtpParticipant(String host, int dataPort, int controlPort) {
        this(host, dataPort, controlPort, generateNewSsrc());
    }

    private RtpParticipant() {
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static RtpParticipant createFromUnexpectedDataPacket(InetSocketAddress origin, DataPacket packet) {
        RtpParticipant participant = new RtpParticipant();
        // I know RFC states that we "MUST NOT" consider the origin IP as the destination for future packets, but it
        // doesn't provide an alternative so yeah, I'll pretty much disregard the RFC here.
        participant.dataAddress = origin;
        participant.controlAddress = new InetSocketAddress(origin.getAddress(), origin.getPort() + 1);
        participant.ssrc = packet.getSsrc();
        return participant;
    }

    public static RtpParticipant createFromSdesChunk(InetSocketAddress origin, SdesChunk chunk) {
        RtpParticipant participant = new RtpParticipant();
        // I know RFC states that we "MUST NOT" consider the origin IP as the destination for future packets, but it
        // doesn't provide an alternative so yeah, I'll pretty much disregard the RFC here.
        participant.dataAddress = new InetSocketAddress(origin.getAddress(), origin.getPort() - 1);
        participant.controlAddress = origin;
        participant.updateFromSdesChunk(chunk);

        return participant;
    }

    /**
     * Randomly generates a new SSRC.
     * <p/>
     * Assuming no other source can obtain the exact same seed (or they're using a different algorithm for the random
     * generation) the probability of collision is roughly 10^-4 when the number of RTP sources is 1000.
     * <a href="http://tools.ietf.org/html/rfc3550#section-8.1">RFC 3550, Section 8.1<a>
     * <p/>
     * In this case, collision odds are slightly bigger because the identifier size will be 31 bits (0x7fffffff,
     * {@link Integer#MAX_VALUE} rather than the full 32 bits.
     *
     * @return A new, random, SSRC identifier.
     */
    public static long generateNewSsrc() {
        return RANDOM.nextInt(Integer.MAX_VALUE);
    }

    // public methods -------------------------------------------------------------------------------------------------

    public boolean updateFromSdesChunk(SdesChunk chunk) {
        boolean modified = false;
        if (this.ssrc != chunk.getSsrc()) {
            modified = true;
        }
        this.ssrc = chunk.getSsrc();
        for (SdesChunkItem item : chunk.getItems()) {
            switch (item.getType()) {
                case CNAME:
                    if (this.willCauseModification(this.cname, item.getValue())) {
                        this.setCname(item.getValue());
                        modified = true;
                    }
                    break;
                case NAME:
                    if (this.willCauseModification(this.name, item.getValue())) {
                        this.setName(item.getValue());
                        modified = true;
                    }
                    break;
                case EMAIL:
                    if (this.willCauseModification(this.email, item.getValue())) {
                        this.setEmail(item.getValue());
                        modified = true;
                    }
                    break;
                case PHONE:
                    if (this.willCauseModification(this.phone, item.getValue())) {
                        this.setPhone(item.getValue());
                        modified = true;
                    }
                    break;
                case LOCATION:
                    if (this.willCauseModification(this.location, item.getValue())) {
                        this.setLocation(item.getValue());
                        modified = true;
                    }
                    break;
                case TOOL:
                    if (this.willCauseModification(this.location, item.getValue())) {
                        this.setTool(item.getValue());
                        modified = true;
                    }
                    break;
                case NOTE:
                    if (this.willCauseModification(this.location, item.getValue())) {
                        this.setNote(item.getValue());
                        modified = true;
                    }
                    break;
                case PRIV:
                    String prefix = ((SdesChunkPrivItem) item).getPrefix();
                    if (this.willCauseModification(this.privPrefix, prefix) ||
                        this.willCauseModification(this.priv, item.getValue())) {
                        this.setPriv(prefix, item.getValue());
                        modified = true;
                    }
                    break;
                default:
                    // Never falls here...
            }
        }

        return modified;
    }

    public void updateDataAddress(SocketAddress address) {
        this.dataAddress = address;
    }

    public void updateControlAddress(SocketAddress address) {
        this.controlAddress = address;
    }

    public long resolveSsrcConflict(long ssrcToAvoid) {
        // Will hardly ever loop more than once...
        while (this.ssrc == ssrcToAvoid) {
            this.ssrc = generateNewSsrc();
        }

        return this.ssrc;
    }

    public long resolveSsrcConflict(Collection<Long> ssrcsToAvoid) {
        // Probability to execute more than once is higher than the other method that takes just a long as parameter,
        // but its still incredibly low: for 1000 participants, there's roughly 2*10^-7 chance of collision
        while (ssrcsToAvoid.contains(this.ssrc)) {
            this.ssrc = generateNewSsrc();
        }

        return this.ssrc;
    }

    /**
     * USE THIS WITH EXTREME CAUTION at the risk of seriously screwing up the way sessions handle data from incoming
     * participants.
     *
     * @param ssrc The new SSRC.
     */
    public void updateSsrc(long ssrc) {
        if ((ssrc < 0) || (ssrc > 0xffffffffL)) {
            throw new IllegalArgumentException("Valid range for SSRC is [0;0xffffffff]");
        }

        this.ssrc = ssrc;
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private boolean willCauseModification(String originalValue, String newValue) {
        return newValue != null && !newValue.equals(originalValue);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public SocketAddress getDataAddress() {
        return this.dataAddress;
    }

    public SocketAddress getControlAddress() {
        return this.controlAddress;
    }

    public long getSsrc() {
        return this.ssrc;
    }

    public String getCname() {
        return this.cname;
    }

    public void setCname(String cname) {
        this.cname = cname;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return this.phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLocation() {
        return this.location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getTool() {
        return this.tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getNote() {
        return this.note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getPrivPrefix() {
        return this.privPrefix;
    }

    public String getPriv() {
        return this.priv;
    }

    public void setPriv(String prefix, String priv) {
        this.privPrefix = prefix;
        this.priv = priv;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append("RtpParticipant{")
                .append("ssrc=").append(this.ssrc)
                .append(", dataAddress=").append(this.dataAddress)
                .append(", controlAddress=").append(this.controlAddress);

        if (this.cname != null) {
            builder.append(", cname='").append(this.cname).append('\'');
        }

        if (this.name != null) {
            builder.append(", name='").append(this.name).append('\'');
        }

        if (this.email != null) {
            builder.append(", email='").append(this.email).append('\'');
        }

        if (this.phone != null) {
            builder.append(", phone='").append(this.phone).append('\'');
        }

        if (this.location != null) {
            builder.append(", location='").append(this.location).append('\'');
        }

        if (this.tool != null) {
            builder.append(", tool='").append(this.tool).append('\'');
        }

        if (this.note != null) {
            builder.append(", note='").append(this.note).append('\'');
        }

        if (this.priv != null) {
            builder.append(", priv='").append(this.privPrefix).append(':').append(this.priv).append('\'');
        }

        return builder.append('}').toString();
    }
}
