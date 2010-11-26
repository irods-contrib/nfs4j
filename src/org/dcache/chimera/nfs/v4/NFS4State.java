/*
 * $Id:NFS4State.java 140 2007-06-07 13:44:55Z tigran $
 */
package org.dcache.chimera.nfs.v4;

import java.security.SecureRandom;
import org.dcache.chimera.nfs.v4.xdr.stateid4;
import org.dcache.chimera.nfs.v4.xdr.uint32_t;
import org.dcache.utils.Bytes;

class NFS4State {            
        
    /*
        struct stateid4 {
            uint32_t        seqid;
            opaque          other[12];
        };

       This structure is used for the various state sharing mechanisms
        between the client and server.  For the client, this data structure
        is read-only.  The starting value of the seqid field is undefined.
        The server is required to increment the seqid field monotonically at
        each transition of the stateid.  This is important since the client
        will inspect the seqid in OPEN stateids to determine the order of
        OPEN processing done by the server.

     */
		        	
    private final stateid4 _stateid;
    private boolean _isConfimed = false;

    /**
     * Random generator to generate stateids.
     */
    private static final SecureRandom RANDOM = new SecureRandom();
    
    public NFS4State(long clientid, int seqid) {

        _stateid = new stateid4();
        _stateid.other = new byte[12];        
        _stateid.seqid = new uint32_t(seqid);
        // generated using a cryptographically strong pseudo random number generator.
        Bytes.putLong(_stateid.other, 0, clientid);
        Bytes.putInt(_stateid.other, 8, RANDOM.nextInt());
    }

    public void bumpSeqid() { ++ _stateid.seqid.value; }
    
    public stateid4 stateid() {
        return _stateid;
    }
    
    public void confirm() {
    	_isConfimed = true;
    }
    
    public boolean isConfimed() {
    	return _isConfimed;
    }
}


/*
 * $Log: NFS4State.java,v $
 * Revision 1.4  2006/07/04 15:29:35  tigran
 * fixed  creation of stateid
 *
 * Revision 1.3  2006/07/04 15:17:49  tigran
 * initial stateid provided by client
 *
 * Revision 1.2  2006/07/04 14:46:12  tigran
 * basic state handling
 *
 * Revision 1.1  2006/06/27 16:29:37  tigran
 * first touch to states
 * TODO: it does not work yet!
 *
 */