/* 
	Description:
		ZK Essentials
	History:
		Created by dennis
Copyright (C) 2012 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.essentials.chapter8;

import java.net.URLEncoder;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.essentials.services.AuthenticationService;
import org.zkoss.essentials.services.UserCredential;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.util.Initiator;

public class AuthenticationInit implements Initiator {

    private static final Logger LOG = 
            LoggerFactory.getLogger(AuthenticationInit.class);
    
	//services
	final AuthenticationService authService = new AuthenticationServiceChapter8Impl();
	
	public void doInit(Page page, Map<String, Object> args) throws Exception {
		
		final String url = Executions.getCurrent().getDesktop().getRequestPath();
		LOG.info("current ctx path: {}, and redirect to login", url);
		UserCredential cre = authService.getUserCredential();
		if(cre==null || cre.isAnonymous()){
			Executions.sendRedirect("/auth/login.zul?from=" + URLEncoder.encode(url, "UTF-8"));
			return;
		}
	}
}