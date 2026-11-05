<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('firstName', 'email', 'username', 'password', 'password-confirm'); section>
    <#if section = "header">
        ${msg("registerTitle")}
    <#elseif section = "form">
        <form id="kc-register-form" action="${url.registrationAction}" method="post">
            <div class="form-group">
                <label class="sn-field-label" for="firstName">${msg("firstName")}</label>
                <input class="${properties.kcInputClass!}" id="firstName" name="firstName" type="text"
                       value="${(register.formData.firstName)!''}" placeholder="${msg('firstNamePlaceholder')}"
                       autocomplete="given-name" required
                       aria-invalid="<#if messagesPerField.existsError('firstName')>true<#else>false</#if>">
                <#if messagesPerField.existsError('firstName')>
                    <span class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${kcSanitize(messagesPerField.get('firstName'))?no_esc}
                    </span>
                </#if>
            </div>
            <div class="form-group">
                <label class="sn-field-label" for="email">${msg("email")}</label>
                <input class="${properties.kcInputClass!}" id="email" name="email" type="email"
                       value="${(register.formData.email)!''}" placeholder="name@company.com"
                       autocomplete="email" required
                       aria-invalid="<#if messagesPerField.existsError('email', 'username')>true<#else>false</#if>">
                <#if messagesPerField.existsError('email', 'username')>
                    <span class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${kcSanitize(messagesPerField.getFirstError('email', 'username'))?no_esc}
                    </span>
                </#if>
            </div>
            <div class="form-group">
                <label class="sn-field-label" for="password">${msg("password")}</label>
                <input class="${properties.kcInputClass!}" id="password" name="password" type="password"
                       placeholder="${msg('passwordPlaceholder')}" autocomplete="new-password" minlength="8" required
                       aria-invalid="<#if messagesPerField.existsError('password', 'password-confirm')>true<#else>false</#if>">
                <#if messagesPerField.existsError('password', 'password-confirm')>
                    <span class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${kcSanitize(messagesPerField.getFirstError('password', 'password-confirm'))?no_esc}
                    </span>
                </#if>
            </div>
            <#-- Keycloak's registration action requires the username and password confirmation. -->
            <input id="registration-username" name="username" type="hidden">
            <input id="password-confirm" name="password-confirm" type="hidden">
            <div class="sn-consent-row">
                <label class="sn-consent" for="termsAccepted">
                    <input id="termsAccepted" name="termsAccepted" type="checkbox" value="on" required
                           aria-invalid="<#if messagesPerField.existsError('termsAccepted')>true<#else>false</#if>">
                    <span>${msg("registrationConsent")}</span>
                </label>
                <#if messagesPerField.existsError('termsAccepted')>
                    <span class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${kcSanitize(messagesPerField.get('termsAccepted'))?no_esc}
                    </span>
                </#if>
            </div>
            <div id="kc-form-buttons">
                <input id="kc-register" type="submit" value="${msg('doRegister')}">
            </div>
            <div class="sn-form-footer">${msg("alreadyHaveAccount")} <a href="${url.loginUrl}">${msg("doLogIn")}</a></div>
        </form>
        <script>
            (() => {
                const form = document.getElementById("kc-register-form");
                const email = form.elements.email;
                const password = form.elements.password;
                const sync = () => {
                    form.elements.username.value = email.value;
                    form.elements["password-confirm"].value = password.value;
                };
                email.addEventListener("input", sync);
                password.addEventListener("input", sync);
                form.addEventListener("submit", sync);
                sync();
            })();
        </script>
    </#if>
</@layout.registrationLayout>
