/*
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program (see the file COPYING.LIB for more
 * details); if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.dcache.chimera.nfs.v4;

import org.dcache.chimera.nfs.v4.xdr.nfsstat4;
import org.dcache.chimera.nfs.v4.xdr.stateid4;
import org.dcache.chimera.nfs.ChimeraNFSException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.dcache.chimera.nfs.v4.xdr.sessionid4;
import org.dcache.utils.Cache;
import org.dcache.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NFSv4StateHandler {

    private static final Logger _log = LoggerFactory.getLogger(NFSv4StateHandler.class);

    private final List<NFS4Client> _clients = new ArrayList<NFS4Client>();

    // all seen by server
    private final Map<String, NFS4Client> _clientsByVerifier = new HashMap<String, NFS4Client>();


    // mapping between server generated clietid and nfs_client_id, not confirmed yet
    private final Map<Long, NFS4Client> _clientsByServerId = new HashMap<Long, NFS4Client>();

    private final Cache<sessionid4, NFSv41Session> _sessionById =
            new Cache<sessionid4, NFSv41Session>("NFSv41 sessions", 5000, Long.MAX_VALUE, TimeUnit.SECONDS.toMillis(NFSv4Defaults.NFS4_LEASE_TIME*2));

    private final Map<String, NFS4Client> _clientByOwner = new HashMap<String, NFS4Client>();

    public void removeClient(NFS4Client client) {

        for(NFSv41Session session: client.sessions() ) {
            _sessionById.remove( session.id() );
        }

        _clientsByServerId.remove(client.id_srv());
        _clientByOwner.remove(client.id());
        _clientsByVerifier.remove( new String( client.verifier() ) ) ;
        _clients.remove(client);

    }

    public void addClient(NFS4Client newClient) {
        _clients.add(newClient);
        _clientsByServerId.put(newClient.id_srv(), newClient);
        _clientsByVerifier.put(new String(newClient.verifier()), newClient);
        _clientByOwner.put( newClient.id(), newClient);
    }

    public NFS4Client getClientByID( Long id) throws ChimeraNFSException {
        NFS4Client client = _clientsByServerId.get(id);
        if(client == null) {
            throw new ChimeraNFSException(nfsstat4.NFS4ERR_STALE_CLIENTID, "bad client id.");
        }
        return client;
    }

    public NFS4Client getClientIdByStateId(stateid4 stateId) throws ChimeraNFSException {
        try {
            return getClientByID(Long.valueOf(Bytes.getLong(stateId.other, 0)));
        }catch(ChimeraNFSException e) {
            throw new ChimeraNFSException(nfsstat4.NFS4ERR_BAD_STATEID, "bad state id.");
        }
    }

    public void addClientByVerifier( byte[] verifier, NFS4Client client) {
        _clientsByVerifier.put(new String(verifier), client );
    }

    public NFS4Client getClientByVerifier(byte[] client) {
        return _clientsByVerifier.get(new String(client));
    }


    public NFSv41Session sessionById( sessionid4 id) {
       return _sessionById.get(id);
    }

    public void sessionById( sessionid4 id, NFSv41Session session) {
        _sessionById.put(id, session);
    }

    public NFS4Client clientByOwner( String ownerid) {
        return _clientByOwner.get(ownerid);
    }

    public void updateClientLeaseTime(stateid4  stateid) throws ChimeraNFSException {

        NFS4Client client = _clientsByServerId.get(Bytes.getLong(stateid.other, 0));
        if(client == null ) {
            throw new ChimeraNFSException( nfsstat4.NFS4ERR_BAD_STATEID, "No client associated with client id."  );
        }

        NFS4State state = client.state(stateid);
        if( state == null) {
            throw new ChimeraNFSException( nfsstat4.NFS4ERR_BAD_STATEID, "State not know to the client."  );
        }

        if( !state.isConfimed() ) {
            throw new ChimeraNFSException( nfsstat4.NFS4ERR_BAD_STATEID, "State is not confirmed"  );
        }

        client.updateLeaseTime(NFSv4Defaults.NFS4_LEASE_TIME);
    }

    public int acquire_state(stateid4  stateid, boolean allow) throws ChimeraNFSException{

        byte[] array_zero = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] array_allOnes ={0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf};

        if ( Arrays.equals(stateid.other, array_zero)){
            if (stateid.seqid.value != 0){
                throw new ChimeraNFSException( nfsstat4.NFS4ERR_BAD_STATEID, "bad seqid.");
            }
            if (allow){
                return 0;
            }
            else{
                throw new ChimeraNFSException( nfsstat4.NFS4ERR_BAD_STATEID, "Can't use 0 as stateid");
            }
        }
        else if (Arrays.equals(stateid.other, array_allOnes)){
            if(stateid.seqid.value == 0xffffffff){
                if (allow){
                    return 1;
                }else{
                    throw new ChimeraNFSException( nfsstat4.NFS4ERR_BAD_STATEID, "Can't use 1 as stateid");
                }
            }else if (stateid.seqid.value == 0){
                //Use Current stateid
                return stateid.seqid.value;
            }else{
                throw new ChimeraNFSException( nfsstat4.NFS4ERR_BAD_STATEID, "bad seqid");
            }
        }
        if (stateid.seqid.value != 0){
            throw new ChimeraNFSException( nfsstat4.NFS4ERR_BAD_STATEID, "bad seqid");
        }
        return stateid.seqid.value;
    }

    public List<NFS4Client> getClients() {
        return new CopyOnWriteArrayList<NFS4Client>(_clients);
    }
}
