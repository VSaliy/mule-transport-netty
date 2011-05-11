/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.netty;

import org.mule.util.concurrent.Latch;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

/**
 *
 */
public class WaitTillDoneFutureListener implements ChannelFutureListener
{
    protected final Latch latch;

    public WaitTillDoneFutureListener(Latch latch)
    {
        this.latch = latch;
    }

    public void operationComplete(ChannelFuture future) throws Exception
    {
        if (!future.isSuccess()) {
            future.getChannel().close();
        }
        // TODO throw error otherwise
        latch.release();
    }
}
