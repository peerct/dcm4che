/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4che.net;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import org.dcm4che.data.Attributes;
import org.dcm4che.net.pdu.AAbort;
import org.dcm4che.net.pdu.AAssociateAC;
import org.dcm4che.net.pdu.AAssociateRJ;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.CommonExtendedNegotiation;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.pdu.RoleSelection;
import org.dcm4che.net.pdu.UserIdentityAC;
import org.dcm4che.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DICOM Part 15, Annex H compliant description of a DICOM network service.
 * <p>
 * A Network AE is an application entity that provides services on a network. A
 * Network AE will have the 16 same functional capability regardless of the
 * particular network connection used. If there are functional differences based
 * on selected network connection, then these are separate Network AEs. If there
 * are 18 functional differences based on other internal structures, then these
 * are separate Network AEs.
 * 
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class ApplicationEntity {

    protected static final Logger LOG = 
            LoggerFactory.getLogger(ApplicationEntity.class);

    private Device device;
    private String aet;
    private String description;
    private byte[][] vendorData = {};
    private String[] applicationClusters = {};
    private String[] prefCalledAETs = {};
    private String[] prefCallingAETs = {};
    private String[] supportedCharacterSets = {};
    private boolean checkCallingAET;
    private boolean acceptor;
    private boolean initiator;
    private Boolean installed;
    private final List<Connection> conns = new ArrayList<Connection>(1);
    private final HashMap<String, TransferCapability> scuTCs =
            new HashMap<String, TransferCapability>();
    private final HashMap<String, TransferCapability> scpTCs =
            new HashMap<String, TransferCapability>();
    private UserIdentityNegotiator userIdNegotiator;
    private DimseRQHandler dimseRQHandler;
    private HashMap<String,Object> properties = new HashMap<String,Object>();

    public ApplicationEntity(String aeTitle) {
        setAETitle(aeTitle);
    }

    /**
     * Get the device that is identified by this application entity.
     * 
     * @return The owning <code>Device</code>.
     */
    public final Device getDevice() {
        return device;
    }

    /**
     * Set the device that is identified by this application entity.
     * 
     * @param device
     *                The owning <code>Device</code>.
     */
    void setDevice(Device device) {
        if (device != null  && this.device != null)
            throw new IllegalStateException("already owned by " + this.device);
        this.device = device;
    }

    /**
     * Get the AE title for this Network AE.
     * 
     * @return A String containing the AE title.
     */
    public final String getAETitle() {
        return aet;
    }

    /**
     * Set the AE title for this Network AE.
     * 
     * @param aet
     *            A String containing the AE title.
     */
    public void setAETitle(String aet) {
        if (aet.isEmpty())
            throw new IllegalArgumentException("AE title cannot be empty");
        Device device = this.device;
        if (device != null)
            device.removeApplicationEntity(this.aet);
        this.aet = aet;
        if (device != null)
            device.addApplicationEntity(this);
    }

    /**
     * Get the description of this network AE
     * 
     * @return A String containing the description.
     */
    public final String getDescription() {
        return description;
    }

    /**
     * Set a description of this network AE.
     * 
     * @param description
     *                A String containing the description.
     */
    public final void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get any vendor information or configuration specific to this network AE.
     * 
     * @return An Object of the vendor data.
     */
    public final byte[][] getVendorData() {
        return vendorData;
    }

    /**
     * Set any vendor information or configuration specific to this network AE
     * 
     * @param vendorData
     *                An Object of the vendor data.
     */
    public final void setVendorData(byte[]... vendorData) {
        this.vendorData = vendorData;
    }

    /**
     * Get the locally defined names for a subset of related applications. E.g.
     * neuroradiology.
     * 
     * @return A String[] containing the names.
     */
    public String[] getApplicationClusters() {
        return applicationClusters;
    }

    public void setApplicationClusters(String... clusters) {
        applicationClusters = clusters;
    }

    /**
     * Get the AE Title(s) that are preferred for initiating associations
     * from this network AE.
     * 
     * @return A String[] of the preferred called AE titles.
     */
    public String[] getPreferredCalledAETitles() {
        return prefCalledAETs;
    }

    public void setPreferredCalledAETitles(String... aets) {
        prefCalledAETs = aets;
    }

    /**
     * Get the AE title(s) that are preferred for accepting associations by
     * this network AE.
     * 
     * @return A String[] containing the preferred calling AE titles.
     */
    public String[] getPreferredCallingAETitles() {
        return prefCallingAETs;
    }

    public void setPreferredCallingAETitles(String... aets) {
        prefCallingAETs = aets;
    }

    public boolean isAcceptOnlyPreferredCallingAETitles() {
        return checkCallingAET;
    }

    public void setAcceptOnlyPreferredCallingAETitles(boolean checkCallingAET) {
        this.checkCallingAET = checkCallingAET;
    }

    /**
     * Get the Character Set(s) supported by the Network AE for data sets it
     * receives. The value shall be selected from the Defined Terms for Specific
     * Character Set (0008,0005) in PS3.3. If no values are present, this
     * implies that the Network AE supports only the default character
     * repertoire (ISO IR 6).
     * 
     * @return A String array of the supported character sets.
     */
    public String[] getSupportedCharacterSets() {
        return supportedCharacterSets;
    }

    /**
     * Set the Character Set(s) supported by the Network AE for data sets it
     * receives. The value shall be selected from the Defined Terms for Specific
     * Character Set (0008,0005) in PS3.3. If no values are present, this
     * implies that the Network AE supports only the default character
     * repertoire (ISO IR 6).
     * 
     * @param characterSets
     *                A String array of the supported character sets.
     */
    public void setSupportedCharacterSets(String... characterSets) {
        supportedCharacterSets = characterSets;
    }

    /**
     * Determine whether or not this network AE can accept associations.
     * 
     * @return A boolean value. True if the Network AE can accept associations,
     *         false otherwise.
     */
    public final boolean isAssociationAcceptor() {
        return acceptor;
    }

    /**
     * Set whether or not this network AE can accept associations.
     * 
     * @param acceptor
     *                A boolean value. True if the Network AE can accept
     *                associations, false otherwise.
     */
    public final void setAssociationAcceptor(boolean acceptor) {
        this.acceptor = acceptor;
    }

    /**
     * Determine whether or not this network AE can initiate associations.
     * 
     * @return A boolean value. True if the Network AE can accept associations,
     *         false otherwise.
     */
    public final boolean isAssociationInitiator() {
        return initiator;
    }

    /**
     * Set whether or not this network AE can initiate associations.
     * 
     * @param initiator
     *                A boolean value. True if the Network AE can accept
     *                associations, false otherwise.
     */
    public final void setAssociationInitiator(boolean initiator) {
        this.initiator = initiator;
    }

    /**
     * Determine whether or not this network AE is installed on a network.
     * 
     * @return A Boolean value. True if the AE is installed on a network. If not
     *         present, information about the installed status of the AE is
     *         inherited from the device
     */
    public boolean isInstalled() {
        return device != null && device.isInstalled() 
                && (installed == null || installed.booleanValue());
    }

    public final Boolean getInstalled() {
        return installed;
    }

    /**
     * Set whether or not this network AE is installed on a network.
     * 
     * @param installed
     *                A Boolean value. True if the AE is installed on a network.
     *                If not present, information about the installed status of
     *                the AE is inherited from the device
     */
    public void setInstalled(Boolean installed) {
        if (installed != null && installed.booleanValue()
                && device != null && !device.isInstalled())
            throw new IllegalStateException("owning device not installed");
        this.installed = installed;
    }

    public DimseRQHandler getDimseRQHandler() {
        DimseRQHandler handler = dimseRQHandler;
        if (handler != null)
            return handler;

        Device device = this.device;
        return device != null
                ? device.getDimseRQHandler()
                : null;
    }

    public final void setDimseRQHandler(DimseRQHandler dimseRQHandler) {
        this.dimseRQHandler = dimseRQHandler;
    }

    public Object getProperty(String key) {
        Object value = properties.get(key);
        if (value != null)
            return value;

        Device device = this.device;
        return device != null
                ? device.getProperty(key)
                : null;
    }

    public Object setProperty(String key, Object value) {
        return properties.put(key, value);
    }

    public Object clearProperty(String key) {
        return properties.remove(key);
    }

    private void checkInstalled() {
        if (!isInstalled())
            throw new IllegalStateException("Not installed");
    }

    private void checkDevice() {
        if (device == null)
            throw new IllegalStateException("Not attached to Device");
    }

    void onDimseRQ(Association as, PresentationContext pc, Attributes cmd,
            PDVInputStream data) throws IOException {
        DimseRQHandler tmp = getDimseRQHandler();
        if (tmp == null) {
            LOG.error("DimseRQHandler not initalized");
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        }
        tmp.onDimseRQ(as, pc, cmd, data);
    }

    public void addConnection(Connection conn) {
        conns.add(conn);
    }

    public boolean removeConnection(Connection conn) {
        return conns.remove(conn);
    }

    public List<Connection> getConnections() {
        return conns;
    }

    public TransferCapability addTransferCapability(TransferCapability tc) {
        return (tc.getRole() == TransferCapability.Role.SCU ? scuTCs : scpTCs)
                .put(tc.getSopClass(), tc);
    }

    public TransferCapability removeTransferCapability(TransferCapability tc) {
        return (tc.getRole() == TransferCapability.Role.SCU ? scuTCs : scpTCs)
                .remove(tc.getSopClass());
    }

    public Collection<TransferCapability> getTransferCapabilities() {
        ArrayList<TransferCapability> tcs =
                new ArrayList<TransferCapability>(scuTCs.size() + scpTCs.size());
        tcs.addAll(scpTCs.values());
        tcs.addAll(scuTCs.values());
        return tcs;
    }

    public TransferCapability getTransferCapabilityFor(
            String sopClass, TransferCapability.Role role) {
        return (role == TransferCapability.Role.SCU ? scuTCs : scpTCs).get(sopClass);
    }

    protected AAssociateAC negotiate(Association as, AAssociateRQ rq)
            throws IOException {
        if (!(isInstalled() && acceptor && conns.contains(as.getConnection())))
            throw new AAssociateRJ(AAssociateRJ.RESULT_REJECTED_PERMANENT,
                    AAssociateRJ.SOURCE_SERVICE_USER,
                    AAssociateRJ.REASON_CALLED_AET_NOT_RECOGNIZED);
        if (checkCallingAET && !contains(prefCallingAETs, rq.getCallingAET()))
            throw new AAssociateRJ(AAssociateRJ.RESULT_REJECTED_PERMANENT,
                    AAssociateRJ.SOURCE_SERVICE_USER,
                    AAssociateRJ.REASON_CALLING_AET_NOT_RECOGNIZED);
        UserIdentityAC userIdentity = userIdNegotiator != null
                ? userIdNegotiator.negotiate(as, rq.getUserIdentityRQ())
                : null;
        if (device.isLimitOfOpenConnectionsExceeded())
            throw new AAssociateRJ(AAssociateRJ.RESULT_REJECTED_TRANSIENT,
                    AAssociateRJ.SOURCE_SERVICE_PROVIDER_ACSE,
                    AAssociateRJ.REASON_LOCAL_LIMIT_EXCEEDED);
        AAssociateAC ac = new AAssociateAC();
        ac.setCalledAET(rq.getCalledAET());
        ac.setCallingAET(rq.getCallingAET());
        Connection conn = as.getConnection();
        ac.setMaxPDULength(conn.getReceivePDULength());
        ac.setMaxOpsInvoked(minZeroAsMax(rq.getMaxOpsInvoked(),
                conn.getMaxOpsPerformed()));
        ac.setMaxOpsPerformed(minZeroAsMax(rq.getMaxOpsPerformed(),
                conn.getMaxOpsInvoked()));
        ac.setUserIdentityAC(userIdentity);
        return negotiate(as, rq, ac);
    }

    private static boolean contains(String[] ss, String val) {
        for (String s : ss)
           if (val.equals(s))
               return true;

        return false;
    }

    protected AAssociateAC negotiate(Association as, AAssociateRQ rq, AAssociateAC ac)
            throws IOException {
        for (PresentationContext rqpc : rq.getPresentationContexts())
            ac.addPresentationContext(negotiate(rq, ac, rqpc));
        return ac;
    }

    static int minZeroAsMax(int i1, int i2) {
        return i1 == 0 ? i2 : i2 == 0 ? i1 : Math.min(i1, i2);
    }

    private PresentationContext negotiate(AAssociateRQ rq, AAssociateAC ac,
           PresentationContext rqpc) {
       String as = rqpc.getAbstractSyntax();
       TransferCapability tc = roleSelection(rq, ac, as);
       int pcid = rqpc.getPCID();
       if (tc == null)
           return new PresentationContext(pcid,
                   PresentationContext.ABSTRACT_SYNTAX_NOT_SUPPORTED,
                   rqpc.getTransferSyntax());

       for (String ts : rqpc.getTransferSyntaxes())
           if (tc.containsTransferSyntax(ts)) {
               byte[] info = negotiate(rq.getExtNegotiationFor(as), tc);
               if (info != null)
                   ac.addExtendedNegotiation(new ExtendedNegotiation(as, info));
               return new PresentationContext(pcid,
                       PresentationContext.ACCEPTANCE, ts);
           }

       return new PresentationContext(pcid,
                PresentationContext.TRANSFER_SYNTAX_NOT_SUPPORTED,
                rqpc.getTransferSyntax());
    }

    private TransferCapability roleSelection(AAssociateRQ rq,
            AAssociateAC ac, String asuid) {
        RoleSelection rqrs = rq.getRoleSelectionFor(asuid);
        if (rqrs == null)
            return getTC(scpTCs, asuid, rq);

        RoleSelection acrs = ac.getRoleSelectionFor(asuid);
        if (acrs != null)
            return getTC(acrs.isSCU() ? scpTCs : scuTCs, asuid, rq);

        TransferCapability tcscu = null;
        TransferCapability tcscp = null;
        boolean scu = rqrs.isSCU()
                && (tcscp = getTC(scpTCs, asuid, rq)) != null;
        boolean scp = rqrs.isSCP()
                && (tcscu = getTC(scuTCs, asuid, rq)) != null;
        ac.addRoleSelection(new RoleSelection(asuid, scu, scp));
        return scu ? tcscp : tcscu;
    }

    private TransferCapability getTC(HashMap<String, TransferCapability> tcs,
            String asuid, AAssociateRQ rq) {
        TransferCapability tc = tcs.get(asuid);
        if (tc != null)
            return tc;

        CommonExtendedNegotiation commonExtNeg =
                rq.getCommonExtendedNegotiationFor(asuid);
        if (commonExtNeg != null) {
            for (String cuid : commonExtNeg.getRelatedGeneralSOPClassUIDs()) {
                tc = tcs.get(cuid);
                if (tc != null)
                    return tc;
            }
            tc = tcs.get(commonExtNeg.getServiceClassUID());
            if (tc != null)
                return tc;
        }

        return tcs.get("*");
    }

    private byte[] negotiate(ExtendedNegotiation exneg, TransferCapability tc) {
        if (exneg == null)
            return null;

        StorageOptions storageOptions = tc.getStorageOptions();
        if (storageOptions != null)
            return storageOptions.toExtendedNegotiationInformation();

        EnumSet<QueryOption> queryOptions = tc.getQueryOptions();
        if (queryOptions != null) {
            EnumSet<QueryOption> commonOpts = QueryOption.toOptions(exneg);
            commonOpts.retainAll(queryOptions);
            return QueryOption.toExtendedNegotiationInformation(commonOpts);
        }
        return null;
    }

    public Association connect(Connection local, Connection remote, AAssociateRQ rq)
            throws IOException, InterruptedException, IncompatibleConnectionException,
                KeyManagementException {
        checkDevice();
        checkInstalled();
        if (rq.getCallingAET() == null)
            rq.setCallingAET(aet);
        rq.setMaxOpsInvoked(local.getMaxOpsInvoked());
        rq.setMaxOpsPerformed(local.getMaxOpsPerformed());
        rq.setMaxPDULength(local.getReceivePDULength());
        Socket sock = local.connect(remote);
        Association as = new Association(this, local, sock);
        as.write(rq);
        as.waitForLeaving(State.Sta5);
        return as;
    }

    public Association connect(Connection remote, AAssociateRQ rq)
            throws IOException, InterruptedException, IncompatibleConnectionException,
                KeyManagementException {
        return connect(findCompatibelConnection(remote), remote, rq);
    }

    public Connection findCompatibelConnection(Connection remoteConn)
            throws IncompatibleConnectionException {
        for (Connection conn : conns)
            if (conn.isCompatible(remoteConn))
                return conn;
        throw new IncompatibleConnectionException(
                "No compatible connection to " + remoteConn + " available on " + this);
    }

    public Association connect(ApplicationEntity remote, AAssociateRQ rq)
        throws IOException, InterruptedException, IncompatibleConnectionException,
            KeyManagementException {
        for (Connection remoteConn : remote.conns)
            for (Connection conn : conns)
                if (conn.isCompatible(remoteConn))
                    return connect(conn, remoteConn, rq);
        throw new IncompatibleConnectionException(
                "No compatible connection to " + remote + " available on " + this);
    }

    protected void onClose(Association as) {
        DimseRQHandler tmp = getDimseRQHandler();
        if (tmp != null)
            tmp.onClose(as);
    }

    @Override
    public String toString() {
        return promptTo(new StringBuilder(512), "").toString();
    }

    public StringBuilder promptTo(StringBuilder sb, String indent) {
        String indent2 = indent + "  ";
        StringUtils.appendLine(sb, indent, "ApplicationEntity[title: ", aet);
        StringUtils.appendLine(sb, indent2,"desc: ", description);
        StringUtils.appendLine(sb, indent2,"acceptor: ", acceptor);
        StringUtils.appendLine(sb, indent2,"initiator: ", initiator);
        StringUtils.appendLine(sb, indent2,"installed: ", getInstalled());
        for (Connection conn : conns)
            conn.promptTo(sb, indent2).append(StringUtils.LINE_SEPARATOR);
        for (TransferCapability tc : getTransferCapabilities())
            tc.promptTo(sb, indent2).append(StringUtils.LINE_SEPARATOR);
        return sb.append(indent).append(']');
    }
}
