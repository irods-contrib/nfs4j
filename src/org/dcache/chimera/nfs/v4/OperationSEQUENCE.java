package org.dcache.chimera.nfs.v4;

import java.util.List;
import org.dcache.chimera.nfs.v4.xdr.nfsstat4;
import org.dcache.chimera.nfs.v4.xdr.sessionid4;
import org.dcache.chimera.nfs.v4.xdr.uint32_t;
import org.dcache.chimera.nfs.v4.xdr.slotid4;
import org.dcache.chimera.nfs.v4.xdr.nfs_argop4;
import org.dcache.chimera.nfs.v4.xdr.nfs_opnum4;
import org.dcache.chimera.nfs.v4.xdr.SEQUENCE4res;
import org.dcache.chimera.nfs.v4.xdr.SEQUENCE4resok;
import org.dcache.chimera.nfs.ChimeraNFSException;
import org.dcache.chimera.nfs.v4.xdr.nfs_resop4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class OperationSEQUENCE extends AbstractNFSv4Operation {

    private static final Logger _log = LoggerFactory.getLogger(OperationSEQUENCE.class);
    private final boolean _trackSession;

    public OperationSEQUENCE(nfs_argop4 args, boolean trackSession ) {
        super(args, nfs_opnum4.OP_SEQUENCE);
        _trackSession = trackSession;
    }

    @Override
    public boolean process(CompoundContext context) {
       SEQUENCE4res res = new SEQUENCE4res();

        try {
            /*
             *
             * from NFSv4.1 spec:
             *
             * This operation MUST appear as the first operation of any COMPOUND in which it appears.
             * The error NFS4ERR_SEQUENCE_POS will be returned when if it is found in any position in
             * a COMPOUND beyond the first. Operations other than SEQUENCE, BIND_CONN_TO_SESSION,
             * EXCHANGE_ID, CREATE_SESSION, and DESTROY_SESSION, may not appear as the first operation
             * in a COMPOUND. Such operations will get the error NFS4ERR_OP_NOT_IN_SESSION if they do
             * appear at the start of a COMPOUND.
             *
             *
             *
             */


            if(context.processedOperations().size() != 0 ) {
                throw new ChimeraNFSException(nfsstat4.NFS4ERR_SEQUENCE_POS, "SEQUENCE not a first operation");
            }

            res.sr_resok4 = new SEQUENCE4resok();

            res.sr_resok4.sr_highest_slotid = new slotid4(_args.opsequence.sa_highest_slotid.value);
            res.sr_resok4.sr_slotid = new slotid4(_args.opsequence.sa_slotid.value);
            res.sr_resok4.sr_target_highest_slotid = new slotid4(_args.opsequence.sa_slotid.value);
            res.sr_resok4.sr_sessionid = new sessionid4(_args.opsequence.sa_sessionid.value);

            NFSv41Session session = NFSv4StateHandler.getInstace().sessionById(_args.opsequence.sa_sessionid);

            if(session == null ) {
                _log.debug("no session for id [{}]",  _args.opsequence.sa_sessionid );
                throw new ChimeraNFSException(nfsstat4.NFS4ERR_BADSESSION, "client not found");
            }

            NFS4Client client = session.getClient();

            if( client.sessionsEmpty(session)) {
                _log.debug("no client for session for id [{}]",  _args.opsequence.sa_sessionid );
                throw new ChimeraNFSException(nfsstat4.NFS4ERR_BADSESSION, "client not found");
            }


            /*
             * in some cases we can ignore lease time update,
             * while client do not keep track of them ( DS )
             *
             */
            if( _trackSession ) {
                List<nfs_resop4> reply;
                if(_args.opsequence.sa_cachethis) {
                    reply = context.processedOperations();
                }else{
                    reply = null;
                }
                if(session.updateSlot(_args.opsequence.sa_slotid.value.value,_args.opsequence.sa_sequenceid.value.value , reply) ) {
                    /*
                     * retransmit + cached reply available.
                     * Stop processing.
                     */
                    return false;
                }
                session.getClient().updateLeaseTime(NFSv4Defaults.NFS4_LEASE_TIME);
            }
            context.setSession(session);

            //res.sr_resok4.sr_sequenceid = new sequenceid4( new uint32_t( session.nextSequenceID()) );
            res.sr_resok4.sr_sequenceid = _args.opsequence.sa_sequenceid;
            res.sr_resok4.sr_status_flags = new uint32_t(0);


            res.sr_status = nfsstat4.NFS4_OK;
        }catch(ChimeraNFSException ne) {
            _log.debug("SQUENCE : {}", ne.getMessage());
            res.sr_status = ne.getStatus();
        }catch(Exception e) {
            _log.error("SEQUENCE :", e);
            res.sr_status = nfsstat4.NFS4ERR_SERVERFAULT;
        }

       _result.opsequence = res;
        context.processedOperations().add(_result);
        return res.sr_status == nfsstat4.NFS4_OK;
    }

}