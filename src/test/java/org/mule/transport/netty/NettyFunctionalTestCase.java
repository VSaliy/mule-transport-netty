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

import org.mule.api.MuleMessage;
import org.mule.api.client.MuleClient;
import org.mule.tck.FunctionalTestCase;

/**
 * TODO
 */
public class NettyFunctionalTestCase extends FunctionalTestCase
{
    @Override
    protected String getConfigResources()
    {
        return "netty-namespace-config.xml";
    }

    public void testNettyConfig() throws Exception
    {
        MuleClient client = muleContext.getClient();
        final MuleMessage result = client.send("tcp://localhost:5000?connector=testClientConnector", "test", null, 10000);
        assertNotNull(result);
        assertNull(result.getExceptionPayload());
        assertEquals("test Received", result.getPayloadAsString());

        //Thread.sleep(1000000);
    }
}
