/*
 * Copyright (c) 2009 - 2012 Deutsches Elektronen-Synchroton,
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY
 *
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
package org.dcache.nfs.v4;

import java.io.IOException;
import org.dcache.nfs.nfsstat;
import org.dcache.nfs.v4.xdr.uint32_t;
import org.dcache.nfs.v4.xdr.nfs_argop4;
import org.dcache.nfs.v4.xdr.nfs_opnum4;
import org.dcache.nfs.v4.xdr.ACCESS4res;
import org.dcache.nfs.v4.xdr.ACCESS4resok;
import org.dcache.nfs.ChimeraNFSException;
import org.dcache.nfs.v4.xdr.nfs_resop4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperationACCESS extends AbstractNFSv4Operation {

    private static final Logger _log = LoggerFactory.getLogger(OperationACCESS.class);

    OperationACCESS(nfs_argop4 args) {
        super(args, nfs_opnum4.OP_ACCESS);
    }

    @Override
    public void process(CompoundContext context, nfs_resop4 result)
            throws ChimeraNFSException, IOException {

        final ACCESS4res res = result.opaccess;
        int requestedAccess = _args.opaccess.access.value;

        int realAccess = context.getFs().access(context.currentInode(), requestedAccess);

        _log.debug("NFS Request ACCESS uid: {} {} {}",
                new Object[]{
                    context.getUser(), requestedAccess, realAccess
                });

        res.resok4 = new ACCESS4resok();
        res.resok4.access = new uint32_t(realAccess);
        res.resok4.supported = new uint32_t(requestedAccess);

        res.status = nfsstat.NFS_OK;
    }
}