<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username', 'password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <#if realm.password>
            <form id="kc-form-login" action="${url.loginAction}" method="post">
                <#if !usernameHidden??>
                    <div class="form-group">
                        <label for="username" class="${properties.kcLabelClass!}">
                            <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                        </label>
                        <input id="username" class="${properties.kcInputClass!}" name="username" type="text"
                               value="${login.username!''}" placeholder="name@company.com"
                               autocomplete="username" autofocus
                               aria-invalid="<#if messagesPerField.existsError('username', 'password')>true<#else>false</#if>">
                        <#if messagesPerField.existsError('username', 'password')>
                            <span class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                ${kcSanitize(messagesPerField.getFirstError('username', 'password'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </#if>
                <div class="form-group">
                    <label for="password" class="${properties.kcLabelClass!}">${msg("password")}</label>
                    <div class="${properties.kcInputGroup!}">
                        <input id="password" class="${properties.kcInputClass!}" name="password" type="password"
                               placeholder="${msg('loginPasswordPlaceholder')}" autocomplete="current-password"
                               aria-invalid="<#if messagesPerField.existsError('username', 'password')>true<#else>false</#if>">
                        <button class="${properties.kcFormPasswordVisibilityButtonClass!}" type="button"
                                aria-label="${msg('showPassword')}" aria-controls="password" data-password-toggle
                                data-icon-show="${properties.kcFormPasswordVisibilityIconShow!}"
                                data-icon-hide="${properties.kcFormPasswordVisibilityIconHide!}"
                                data-label-show="${msg('showPassword')}" data-label-hide="${msg('hidePassword')}">
                            <i class="${properties.kcFormPasswordVisibilityIconShow!}" aria-hidden="true"></i>
                        </button>
                    </div>
                    <#if usernameHidden?? && messagesPerField.existsError('username', 'password')>
                        <span class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.getFirstError('username', 'password'))?no_esc}
                        </span>
                    </#if>
                </div>
                <div class="login-pf-settings">
                    <div id="kc-form-options">
                        <#if realm.rememberMe && !usernameHidden??>
                            <div class="checkbox"><label>
                                <input id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if>>
                                ${msg("rememberMe")}
                            </label></div>
                        </#if>
                    </div>
                    <#if realm.resetPasswordAllowed>
                        <a href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
                    </#if>
                </div>
                <div id="kc-form-buttons">
                    <input type="hidden" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>>
                    <input id="kc-login" name="login" type="submit" value="${msg('doLogIn')}">
                </div>
            </form>
            <script type="module" src="${url.resourcesPath}/js/passwordVisibility.js"></script>
        </#if>
    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration">${msg("noAccount")} <a href="${url.registrationUrl}">${msg("doRegister")}</a></div>
        </#if>
    <#elseif section = "socialProviders">
        <#if realm.password && social?? && social.providers?has_content>
            <div id="kc-social-providers" class="${properties.kcFormSocialAccountSectionClass!}">
                <hr>
                <h2>${msg("identity-provider-login-label")}</h2>
                <ul class="${properties.kcFormSocialAccountListClass!}">
                    <#list social.providers as provider>
                        <li><a href="${provider.loginUrl}" id="social-${provider.alias}">
                            ${provider.displayName!}
                        </a></li>
                    </#list>
                </ul>
            </div>
        </#if>
    </#if>
</@layout.registrationLayout>
