/* 
	Description:
		ZK Essentials
	History:
		Created by dennis
Copyright (C) 2012 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.essentials.chapter8;

import java.net.URLDecoder;

import org.zkoss.essentials.services.AuthenticationService;
import org.zkoss.essentials.services.UserCredential;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;

public class LoginController extends SelectorComposer<Component> {
	private static final long serialVersionUID = 1L;
	
	//wire components
	@Wire
	Textbox account;
	@Wire
	Textbox password;
	@Wire
	Label message;
	
	//services
	AuthenticationService authService = new AuthenticationServiceChapter8Impl();
	
	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);
		
		this._from = Executions.getCurrent().getParameter("from");
	}


	@Listen("onClick=#login; onOK=#loginWin")
	public void doLogin() throws Exception {
		String nm = account.getValue();
		String pd = password.getValue();
		
		if(!authService.login(nm,pd)){
			message.setValue("account or password are not correct.");
			return;
		}
		
		final UserCredential cre= authService.getUserCredential();
		message.setValue("Welcome, "+cre.getName());
		message.setSclass("");
		
		if (null != _from) {
			Executions.sendRedirect(URLDecoder.decode(_from, "UTF-8"));
		} else {
			Executions.sendRedirect("/");
		}
	}
	
	private String _from;
}
