<#import "template.ftl" as layout>
<@layout.registrationLayout bodyClass="oauth"; section>
    <#if section = "header">
        <#if client.name?has_content>
            ${msg("oauthGrantTitle",advancedMsg(client.name))}
        <#else>
            ${msg("oauthGrantTitle",client.clientId)}
        </#if>
    <#elseif section = "form">
        <div id="kc-oauth" class="content-area">
            <h3>${msg("oauthGrantRequest")}</h3>
            
            <form class="form-horizontal" action="${url.oauthAction}" method="POST">
	                <#if oauth.clientScopesRequested??>
	                    <#list oauth.clientScopesRequested as clientScope>
	                    	<div class=${properties.kcFormGroupClass!}>
	                        	<div class="col-sm-8 col-md-8">
	                            	${advancedMsg(clientScope.consentScreenText)}
	                        	</div>
	                        	<div class="col-sm-2 col-md-2">
	                        		<#if clientScope.consented && clientScope.name?has_content>
	                        			<input id="${clientScope.name}" type="checkbox" checked disabled>
	                        			<input name="${clientScope.name}" type="hidden" value="on">
	                        		<#elseif clientScope.name?has_content>
	                        			<input id="${clientScope.name}" name="${clientScope.name}" type="checkbox">
	                        		</#if>
		                        </div>
        	                </div>
    	                </#list>
	                </#if>

                <input type="hidden" name="code" value="${oauth.code}">
                <div class="${properties.kcFormGroupClass!}">
                    <div id="kc-form-buttons">
                        <div class="col-md-offset-8 col-md-4">
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" name="accept" id="kc-login" type="submit" value="${msg("doYes")}"/>
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" name="cancel" id="kc-cancel" type="submit" value="${msg("doNo")}"/>
                        </div>
                    </div>
                </div>
            </form>
            <div class="clearfix"></div>
        </div>
    </#if>
</@layout.registrationLayout>
