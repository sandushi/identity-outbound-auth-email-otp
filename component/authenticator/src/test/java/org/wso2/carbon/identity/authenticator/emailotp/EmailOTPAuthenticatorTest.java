/*
 *  Copyright (c) 2017, WSO2 LLC. (https://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.identity.authenticator.emailotp;

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.description.TransportOutDescription;
import org.apache.axis2.engine.AxisConfiguration;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockObjectFactory;
import org.powermock.reflect.Whitebox;
import org.testng.Assert;
import org.testng.IObjectFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.ObjectFactory;
import org.testng.annotations.Test;
import org.wso2.carbon.extension.identity.helper.FederatedAuthenticatorUtil;
import org.wso2.carbon.identity.application.authentication.framework.AuthenticatorFlowStatus;
import org.wso2.carbon.identity.application.authentication.framework.LocalApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.config.ConfigurationFacade;
import org.wso2.carbon.identity.application.authentication.framework.config.builder.FileBasedConfigurationBuilder;
import org.wso2.carbon.identity.application.authentication.framework.config.model.AuthenticatorConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.SequenceConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.StepConfig;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.AuthenticationFailedException;
import org.wso2.carbon.identity.application.authentication.framework.exception.LogoutFailedException;
import org.wso2.carbon.identity.application.authentication.framework.internal.FrameworkServiceDataHolder;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedIdPData;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.authenticator.emailotp.config.EmailOTPUtils;
import org.wso2.carbon.identity.authenticator.emailotp.internal.EmailOTPServiceDataHolder;
import org.wso2.carbon.identity.core.ServiceURL;
import org.wso2.carbon.identity.core.ServiceURLBuilder;
import org.wso2.carbon.identity.core.URLBuilderException;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.carbon.identity.mgt.IdentityMgtConfigException;
import org.wso2.carbon.identity.mgt.IdentityMgtServiceException;
import org.wso2.carbon.identity.mgt.config.Config;
import org.wso2.carbon.identity.mgt.config.ConfigBuilder;
import org.wso2.carbon.identity.mgt.config.ConfigType;
import org.wso2.carbon.identity.mgt.config.StorageType;
import org.wso2.carbon.identity.mgt.mail.Notification;
import org.wso2.carbon.identity.mgt.mail.NotificationBuilder;
import org.wso2.carbon.identity.mgt.mail.NotificationData;
import org.wso2.carbon.user.api.Claim;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.common.User;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants.USERNAME_CLAIM;
import static org.wso2.carbon.identity.authenticator.emailotp.EmailOTPAuthenticatorTestConstants.DUMMY_LOGIN_PAGE_URL;
import static org.wso2.carbon.identity.authenticator.emailotp.EmailOTPAuthenticatorTestConstants.EMAIL_ADDRESS;
import static org.wso2.carbon.identity.authenticator.emailotp.EmailOTPAuthenticatorTestConstants.TENANT_DOMAIN;
import static org.wso2.carbon.identity.authenticator.emailotp.EmailOTPAuthenticatorTestConstants.TENANT_ID;
import static org.wso2.carbon.identity.authenticator.emailotp.EmailOTPAuthenticatorTestConstants.USER_NAME;

@PrepareForTest({EmailOTPAuthenticator.class, FileBasedConfigurationBuilder.class, FederatedAuthenticatorUtil.class,
        FrameworkUtils.class, MultitenantUtils.class, IdentityTenantUtil.class, ConfigurationContextFactory.class,
        ConfigBuilder.class, NotificationBuilder.class, EmailOTPUtils.class, EmailOTPServiceDataHolder.class,
        ServiceURLBuilder.class, AbstractUserStoreManager.class, OneTimePassword.class, UserCoreUtil.class,
        ConfigurationFacade.class, FrameworkServiceDataHolder.class})
@PowerMockIgnore({"javax.crypto.*"})
public class EmailOTPAuthenticatorTest {
    private EmailOTPAuthenticator emailOTPAuthenticator;
    @Spy
    private AuthenticatorConfig authenticatorConfig;
    @Spy
    private AuthenticationContext context;

    private HttpServletRequest httpServletRequest;
    private EmailOTPAuthenticator mockedEmailOTPAuthenticator;
    private EmailOTPAuthenticator spiedEmailOTPAuthenticator;
    private HttpServletResponse httpServletResponse;
    private FileBasedConfigurationBuilder fileBasedConfigurationBuilder;
    private StepConfig stepConfig;
    private RealmService realmService;
    private UserRealm userRealm;
    private AbstractUserStoreManager userStoreManager;
    private AuthenticatedUser authUser;
    private LocalApplicationAuthenticator localApplicationAuthenticator;
    private ClaimManager claimManager;
    private Claim claim;
    private ConfigurationFacade configurationFacade;
    private ConfigurationContext configurationContext;
    private AxisConfiguration axisConfiguration;
    private ConfigBuilder configBuilder;
    private Config config;
    private HashMap<String, TransportOutDescription> transportOutDescriptionHashMap;
    private Notification notification;
    private FrameworkServiceDataHolder frameworkServiceDataHolder;

    @BeforeMethod
    public void setUp() throws Exception {
        emailOTPAuthenticator = new EmailOTPAuthenticator();
        initMocks(this);
        httpServletRequest = mock(HttpServletRequest.class);
        mockedEmailOTPAuthenticator = mock(EmailOTPAuthenticator.class);
        spiedEmailOTPAuthenticator = PowerMockito.spy(new EmailOTPAuthenticator());
        httpServletResponse = mock(HttpServletResponse.class);
        fileBasedConfigurationBuilder = mock(FileBasedConfigurationBuilder.class);
        stepConfig = mock(StepConfig.class);
        realmService = mock(RealmService.class);
        userRealm = mock(UserRealm.class);
        userStoreManager = mock(AbstractUserStoreManager.class);
        authUser = mock(AuthenticatedUser.class);
        localApplicationAuthenticator = mock(LocalApplicationAuthenticator.class);
        claimManager = mock(ClaimManager.class);
        claim = mock(Claim.class);
        context = PowerMockito.spy(new AuthenticationContext());
        configurationFacade = mock(ConfigurationFacade.class);
        configurationContext = mock(ConfigurationContext.class);
        axisConfiguration = mock(AxisConfiguration.class);
        transportOutDescriptionHashMap = mock(HashMap.class);
        configBuilder = mock(ConfigBuilder.class);
        config = mock(Config.class);
        notification = mock(Notification.class);
        frameworkServiceDataHolder = mock(FrameworkServiceDataHolder.class);

        mockStatic(FileBasedConfigurationBuilder.class);
        mockStatic(EmailOTPServiceDataHolder.class);
        mockStatic(FederatedAuthenticatorUtil.class);
        mockStatic(FrameworkUtils.class);
        mockStatic(UserCoreUtil.class);
        mockStatic(MultitenantUtils.class);
        mockStatic(IdentityTenantUtil.class);
        mockStatic(ConfigurationContextFactory.class);
        mockStatic(ConfigBuilder.class);
        mockStatic(NotificationBuilder.class);
        mockStatic(EmailOTPUtils.class);
        mockStatic(OneTimePassword.class);
        mockStatic(ConfigurationFacade.class);
        mockStatic(ConfigurationContextFactory.class);
        mockStatic(NotificationBuilder.class);
        mockStatic(FrameworkServiceDataHolder.class);

        EmailOTPServiceDataHolder emailOTPServiceDataHolder = mock(EmailOTPServiceDataHolder.class);
        IdentityEventService identityEventService = mock(IdentityEventService.class);
        when(EmailOTPServiceDataHolder.getInstance()).thenReturn(emailOTPServiceDataHolder);
        when(emailOTPServiceDataHolder.getIdentityEventService()).thenReturn(identityEventService);
        Mockito.doNothing().when(identityEventService).handleEvent(anyObject());

        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setUserName(USER_NAME);
        authenticatedUser.setUserStoreDomain("secondary");
        authenticatedUser.setUserId(EmailOTPAuthenticatorTestConstants.USER_ID);
        when(context.getProperty(EmailOTPAuthenticatorConstants.AUTHENTICATED_USER)).thenReturn(authenticatedUser);

        mockServiceURLBuilder();
    }

    @Test(description = "Test case for canHandle() method true case.")
    public void testCanHandle() throws Exception {
        when(httpServletRequest.getParameter(EmailOTPAuthenticatorConstants.RESEND)).thenReturn("true");
        Assert.assertTrue(emailOTPAuthenticator.canHandle(httpServletRequest));
    }

    @Test(description = "Test case for canHandle() method false case.")
    public void testCanHandleFalse() throws Exception {
        when(httpServletRequest.getParameter(EmailOTPAuthenticatorConstants.RESEND)).thenReturn(null);
        when(httpServletRequest.getParameter(EmailOTPAuthenticatorConstants.CODE)).thenReturn(null);
        when(httpServletRequest.getParameter(EmailOTPAuthenticatorConstants.EMAIL_ADDRESS)).thenReturn(null);
        when(httpServletRequest.getParameter(EmailOTPAuthenticatorConstants.USER_NAME)).thenReturn(null);
        Assert.assertFalse(emailOTPAuthenticator.canHandle(httpServletRequest));
    }

    @Test(description = "Test case for getContextIdentifier() method.")
    public void testGetContextIdentifier() {
        when(httpServletRequest.getParameter("sessionDataKey")).thenReturn("234567890");
        Assert.assertEquals(emailOTPAuthenticator.getContextIdentifier(httpServletRequest), "234567890");

        when(httpServletRequest.getParameter("sessionDataKey")).thenReturn(null);
        Assert.assertNull(emailOTPAuthenticator.getContextIdentifier(httpServletRequest));
    }

    @Test(description = "Test case for getFriendlyName() method.")
    public void testGetFriendlyName() {
        Assert.assertEquals(emailOTPAuthenticator.getFriendlyName(),
                EmailOTPAuthenticatorConstants.AUTHENTICATOR_FRIENDLY_NAME);
    }

    @Test(description = "Test case for getAuthenticatorName() method.")
    public void testGetName() {
        Assert.assertEquals(emailOTPAuthenticator.getName(), EmailOTPAuthenticatorConstants.AUTHENTICATOR_NAME);
    }

    @Test(description = "Test case for retryAuthenticationEnabled() method.")
    public void testRetryAuthenticationEnabled() throws Exception {
        Assert.assertTrue(Whitebox.invokeMethod(emailOTPAuthenticator, "retryAuthenticationEnabled"));
    }

    @Test(description = "Test case for successful logout request.")
    public void testProcessLogoutRequest() throws Exception {
        when(context.isLogoutRequest()).thenReturn(true);
        doReturn(true).when(mockedEmailOTPAuthenticator).canHandle(httpServletRequest);
        AuthenticatorFlowStatus status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.SUCCESS_COMPLETED);
    }

    @Test(description = "Test case for process() method when authenticated user is null " +
            "and the username of an existing user is entered into the IdF page.")
    public void testProcessWithoutAuthenticatedUserAndValidUsernameEntered()
            throws Exception {

        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "true");
        parameters.put(EmailOTPAuthenticatorConstants.SEND_OTP_TO_FEDERATED_EMAIL_ATTRIBUTE, "true");

        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        authenticatorConfig.setParameterMap(parameters);
        setStepConfigWithEmailOTPAuthenticator(authenticatorConfig, context);

        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(ConfigurationFacade.getInstance()).thenReturn(configurationFacade);
        when(configurationFacade.getAuthenticationEndpointURL()).thenReturn(DUMMY_LOGIN_PAGE_URL);

        AuthenticatorFlowStatus status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
        Assert.assertTrue(((boolean) context.getProperty(
                EmailOTPAuthenticatorConstants.IS_IDF_INITIATED_FROM_AUTHENTICATOR)));

        // Resolving the user object.
        when(httpServletRequest.getParameter(EmailOTPAuthenticatorConstants.USER_NAME))
                .thenReturn(USER_NAME);
        when(FrameworkUtils.preprocessUsername(anyString(), any(AuthenticationContext.class)))
                .thenReturn(USER_NAME + "@" + EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(UserCoreUtil.extractDomainFromName(anyString())).thenReturn("PRIMARY");
        when(MultitenantUtils.getTenantAwareUsername(anyString())).thenReturn(USER_NAME);
        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(FederatedAuthenticatorUtil.isUserExistInUserStore(anyString())).thenReturn(true);
        when(IdentityTenantUtil.getTenantId(EmailOTPAuthenticatorConstants.SUPER_TENANT)).thenReturn(TENANT_ID);
        when(userStoreManager.getUserClaimValue(
                USER_NAME,
                EmailOTPAuthenticatorConstants.EMAIL_CLAIM,
                null)).thenReturn(EMAIL_ADDRESS);
        when(UserCoreUtil.addTenantDomainToEntry(
                USER_NAME,
                EmailOTPAuthenticatorConstants.SUPER_TENANT))
            .thenReturn(
                USER_NAME + "@" + EmailOTPAuthenticatorConstants.SUPER_TENANT);
        mockUserRealm();
        User user = new User(UUID.randomUUID().toString(), USER_NAME, null);
        user.setUserStoreDomain("PRIMARY");
        user.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        List<User> userList = new ArrayList<>();
        userList.add(user);
        when(userStoreManager.getUserListWithID(USERNAME_CLAIM, USER_NAME, null)).thenReturn(userList);
        mockSendOTP();

        status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse, context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
        Assert.assertTrue((Boolean.parseBoolean(String.valueOf(context.getProperty(
                EmailOTPAuthenticatorConstants.IS_REDIRECT_TO_EMAIL_OTP)))));
    }

    @Test(description = "Test case for process() method when authenticated user is null and the username of a" +
            "non existing user is entered into the IdF page.")
    public void testProcessWithoutAuthenticatedUserAndInvalidUsernameEntered() throws Exception {

        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "true");
        parameters.put(EmailOTPAuthenticatorConstants.SEND_OTP_TO_FEDERATED_EMAIL_ATTRIBUTE, "true");

        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        authenticatorConfig.setParameterMap(parameters);
        setStepConfigWithEmailOTPAuthenticator(authenticatorConfig, context);

        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(ConfigurationFacade.getInstance()).thenReturn(configurationFacade);
        when(configurationFacade.getAuthenticationEndpointURL()).thenReturn(DUMMY_LOGIN_PAGE_URL);

        AuthenticatorFlowStatus status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
        Assert.assertTrue((boolean) context.getProperty(
                EmailOTPAuthenticatorConstants.IS_IDF_INITIATED_FROM_AUTHENTICATOR));

        // Resolving the user object.
        when(httpServletRequest.getParameter(EmailOTPAuthenticatorConstants.USER_NAME))
                .thenReturn(USER_NAME);
        when(FrameworkUtils.preprocessUsername(anyString(), any(AuthenticationContext.class)))
                .thenReturn(USER_NAME + "@" + TENANT_DOMAIN);
        when(UserCoreUtil.extractDomainFromName(anyString())).thenReturn("PRIMARY");
        when(MultitenantUtils.getTenantAwareUsername(anyString())).thenReturn(USER_NAME);
        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(TENANT_DOMAIN);
        when(FederatedAuthenticatorUtil.isUserExistInUserStore(anyString())).thenReturn(false);
        mockUserRealm();
        when(userStoreManager.getUserListWithID(USERNAME_CLAIM, USER_NAME, null))
                .thenReturn(new ArrayList<User>());

        status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
        Assert.assertTrue((Boolean.parseBoolean(String.valueOf(context.getProperty(
                EmailOTPAuthenticatorConstants.IS_REDIRECT_TO_EMAIL_OTP)))));
    }

    @Test(description = "Test case for process() method when email OTP is optional for local user")
    public void testProcessWithEmailOTPOptional() throws Exception {

        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "false");
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        context.setSubject(authenticatedUser);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(anyString(), anyString(), anyString()))
                .thenReturn(null);
        setStepConfigWithBasicAuthenticator(authenticatedUser, authenticatorConfig);
        mockUserRealm();
        when(MultitenantUtils.getTenantAwareUsername(USER_NAME))
                .thenReturn(USER_NAME);
        when(userStoreManager.getUserClaimValue(USER_NAME,
                EmailOTPAuthenticatorConstants.EMAIL_CLAIM, null))
                .thenReturn(EMAIL_ADDRESS);
        emailOTPAuthenticator = PowerMockito.spy(new EmailOTPAuthenticator());
        // Mocking the random number generation since algorithm DRBG is not supported in java 8. Revert this when
        // source is compatible with java 11.
        when(OneTimePassword.getRandomNumber(EmailOTPAuthenticatorConstants.SECRET_KEY_LENGTH)).thenReturn("123456");
        doNothing().when(emailOTPAuthenticator, "sendOTP", anyString(), anyString(), anyString(), anyObject(),
                anyString());
        AuthenticatorFlowStatus status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @Test(description = "Test case for process() method when email OTP is mandatory for local user.")
    public void testProcessWithEmailOTPMandatory() throws Exception {

        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "true");
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        context.setSubject(authenticatedUser);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(anyString(), anyString(), anyString()))
                .thenReturn(null);
        setStepConfigWithBasicAuthenticator(authenticatedUser, authenticatorConfig);
        mockUserRealm();
        when(MultitenantUtils.getTenantAwareUsername(USER_NAME))
                .thenReturn(USER_NAME);
        when(userStoreManager.getUserClaimValue(USER_NAME,
                EmailOTPAuthenticatorConstants.EMAIL_CLAIM, null))
                .thenReturn(EMAIL_ADDRESS);
        emailOTPAuthenticator = PowerMockito.spy(new EmailOTPAuthenticator());
        // Mocking the random number generation since algorithm DRBG is not supported in java 8. Revert this when
        // source is compatible with java 11.
        when(OneTimePassword.getRandomNumber(EmailOTPAuthenticatorConstants.SECRET_KEY_LENGTH)).thenReturn("123456");
        doNothing().when(emailOTPAuthenticator, "sendOTP", anyString(), anyString(), anyString(), anyObject(),
                anyString());
        AuthenticatorFlowStatus status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @Test(description = "Test case for process() method when email OTP is mandatory and user disabled email OTP.")
    public void testProcessWhenEmailOTPIsMandatoryAndUserDisabledEmailOTP()
            throws Exception {

        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "true");
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_ENABLE_BY_USER, "true");
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        context.setSubject(authenticatedUser);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(anyString(), anyString(), anyString()))
                .thenReturn(null);
        when(stepConfig.getAuthenticatedAutenticator()).thenReturn(authenticatorConfig);
        when(FederatedAuthenticatorUtil.isUserExistInUserStore(anyString())).thenReturn(true);
        setStepConfigWithBasicAuthenticator(authenticatedUser, authenticatorConfig);
        mockUserRealm();
        when(MultitenantUtils.getTenantAwareUsername(USER_NAME))
                .thenReturn(USER_NAME);
        Map<String, String> claimMap = new HashMap<>();
        claimMap.put(EmailOTPAuthenticatorConstants.USER_EMAILOTP_DISABLED_CLAIM_URI, "true");
        when(userStoreManager.getUserClaimValuesWithID(EmailOTPAuthenticatorTestConstants.USER_ID,
                new String[] {EmailOTPAuthenticatorConstants.USER_EMAILOTP_DISABLED_CLAIM_URI}, null))
                .thenReturn(claimMap);
        when(userStoreManager.getUserClaimValue(USER_NAME,
                EmailOTPAuthenticatorConstants.EMAIL_CLAIM, null))
                .thenReturn(EmailOTPAuthenticatorTestConstants.EMAIL_ADDRESS);
        // Mocking the random number generation since algorithm DRBG is not supported in java 8. Revert this when
        // source is compatible with java 11.
        when(OneTimePassword.getRandomNumber(EmailOTPAuthenticatorConstants.SECRET_KEY_LENGTH)).thenReturn("123456");
        doNothing().when(spiedEmailOTPAuthenticator, "sendOTP", anyString(), anyString(), anyString(), anyObject(),
                anyString());
        AuthenticatorFlowStatus status = spiedEmailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @Test(description = "Test case for process() method when email OTP is mandatory and user enabled email OTP.")
    public void testProcessWhenEmailOTPIsMandatoryAndUserEnabledEmailOTP() throws Exception {

        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "true");
        parameters.put(EmailOTPAuthenticatorConstants.SEND_OTP_TO_FEDERATED_EMAIL_ATTRIBUTE, "true");
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_ENABLE_BY_USER, "true");
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        context.setSubject(authenticatedUser);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(anyString(), anyString(), anyString()))
                .thenReturn(null);
        when(stepConfig.getAuthenticatedAutenticator()).thenReturn(authenticatorConfig);
        when(FederatedAuthenticatorUtil.isUserExistInUserStore(anyString())).thenReturn(true);
        setStepConfigWithBasicAuthenticator(authenticatedUser, authenticatorConfig);
        mockUserRealm();
        when(MultitenantUtils.getTenantAwareUsername(USER_NAME))
                .thenReturn(USER_NAME);
        Map<String, String> claimMap = new HashMap<>();
        claimMap.put(EmailOTPAuthenticatorConstants.USER_EMAILOTP_DISABLED_CLAIM_URI, "false");
        when(userStoreManager.getUserClaimValuesWithID(EmailOTPAuthenticatorTestConstants.USER_ID,
                new String[] {EmailOTPAuthenticatorConstants.USER_EMAILOTP_DISABLED_CLAIM_URI}, null))
                .thenReturn(claimMap);
        when(userStoreManager.getUserClaimValue(USER_NAME,
                EmailOTPAuthenticatorConstants.EMAIL_CLAIM, null))
                .thenReturn(EmailOTPAuthenticatorTestConstants.EMAIL_ADDRESS);
        // Mocking the random number generation since algorithm DRBG is not supported in java 8. Revert this when
        // source is compatible with java 11.
        when(OneTimePassword.getRandomNumber(EmailOTPAuthenticatorConstants.SECRET_KEY_LENGTH)).thenReturn("123456");
        emailOTPAuthenticator = PowerMockito.spy(new EmailOTPAuthenticator());
        doNothing().when(emailOTPAuthenticator, "sendOTP", anyString(), anyString(), anyString(), anyObject(),
                anyString());
        AuthenticatorFlowStatus status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @Test(description = "Test case for process() method when email OTP is optional and user disabled email OTP.")
    public void testProcessWhenEmailOTPIsOptionalAndUserDisabledEmailOTP() throws AuthenticationFailedException,
            LogoutFailedException, UserStoreException {

        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "false");
        parameters.put(EmailOTPAuthenticatorConstants.SEND_OTP_TO_FEDERATED_EMAIL_ATTRIBUTE, "true");
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_ENABLE_BY_USER, "true");
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        context.setSubject(authenticatedUser);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(anyString(), anyString(), anyString()))
                .thenReturn(null);
        when(stepConfig.getAuthenticatedAutenticator()).thenReturn(authenticatorConfig);
        when(FederatedAuthenticatorUtil.isUserExistInUserStore(anyString())).thenReturn(true);
        setStepConfigWithBasicAuthenticator(authenticatedUser, authenticatorConfig);
        mockUserRealm();
        when(MultitenantUtils.getTenantAwareUsername(USER_NAME))
                .thenReturn(USER_NAME);
        Map<String, String> claimMap = new HashMap<>();
        claimMap.put(EmailOTPAuthenticatorConstants.USER_EMAILOTP_DISABLED_CLAIM_URI, "true");
        when(userStoreManager.getUserClaimValues(USER_NAME,
                new String[] {EmailOTPAuthenticatorConstants.USER_EMAILOTP_DISABLED_CLAIM_URI}, null))
                .thenReturn(claimMap);
        AuthenticatorFlowStatus status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.SUCCESS_COMPLETED);
    }

    private void mockUserRealm() throws UserStoreException {
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        when(FrameworkServiceDataHolder.getInstance()).thenReturn(frameworkServiceDataHolder);
        when(frameworkServiceDataHolder.getRealmService()).thenReturn(realmService);
    }

    private void mockSendOTP() throws AxisFault, IdentityMgtConfigException, IdentityMgtServiceException {
        when(ConfigurationContextFactory.createConfigurationContextFromFileSystem(null, null))
                .thenReturn(configurationContext);
        when(configurationContext.getAxisConfiguration()).thenReturn(axisConfiguration);
        when(axisConfiguration.getTransportsOut()).thenReturn(transportOutDescriptionHashMap);
        when(transportOutDescriptionHashMap.containsKey(EmailOTPAuthenticatorConstants.TRANSPORT_MAILTO))
                .thenReturn(true);
        when(ConfigBuilder.getInstance()).thenReturn(configBuilder);
        when(configBuilder.loadConfiguration(ConfigType.EMAIL, StorageType.REGISTRY, TENANT_ID)).thenReturn(config);
        Properties properties = new Properties();
        properties.setProperty(EmailOTPAuthenticatorConstants.AUTHENTICATOR_NAME, "Email OTP");
        when(config.getProperties()).thenReturn(properties);
        when(NotificationBuilder.createNotification(anyString(), anyString(), any(NotificationData.class)))
                .thenReturn(notification);

    }

    @Test(expectedExceptions = {AuthenticationFailedException.class})
    public void testIsEmailOTPDisableForUserException() throws Exception {
        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(null);
        Whitebox.invokeMethod(emailOTPAuthenticator, "isEmailOTPDisableForUser", anyString(), context,
                new HashMap<>());
    }

    @Test
    public void testIsEmailOTPDisableForUser() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_ENABLE_BY_USER, "true");
        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        when(userStoreManager.getUserClaimValue(USER_NAME,
                EmailOTPAuthenticatorConstants.USER_EMAILOTP_DISABLED_CLAIM_URI, null)).thenReturn("true");
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);

        Whitebox.invokeMethod(emailOTPAuthenticator, "isEmailOTPDisableForUser", anyString(), context,
                parameters);
    }

    @Test(description = "Test case for process() method when email OTP is mandatory for federated user.")
    public void testProcessWhenEmailOTPIsMandatoryWithFederatedEmail() throws Exception {

        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "true");
        parameters.put(EmailOTPAuthenticatorConstants.SEND_OTP_TO_FEDERATED_EMAIL_ATTRIBUTE, "true");
        parameters.put(EmailOTPAuthenticatorConstants.FEDERATED_EMAIL_ATTRIBUTE_KEY, "email");
        parameters.put(EmailOTPAuthenticatorConstants.EMAIL_API, "EmailAPI");
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        context.setSubject(authenticatedUser);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        context.setAuthenticatorProperties(parameters);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(userRealm);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(anyString(), anyString(), anyString()))
                .thenReturn(null);
        setStepConfigWithFederatedAuthenticator(authenticatedUser, authenticatorConfig);
        mockFederatedEmailAttributeKey(parameters, authenticatedUser, EMAIL_ADDRESS);
        emailOTPAuthenticator = PowerMockito.spy(new EmailOTPAuthenticator());
        // Mocking the random number generation since algorithm DRBG is not supported in java 8. Revert this when
        // source is compatible with java 11.
        when(OneTimePassword.getRandomNumber(EmailOTPAuthenticatorConstants.SECRET_KEY_LENGTH)).thenReturn("123456");
        doNothing().when(emailOTPAuthenticator, "sendOTP", anyString(), anyString(), anyString(), anyObject(),
                anyString());
        AuthenticatorFlowStatus status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @Test(description = "Test case for process() method when email OTP is optional for federated user.")
    public void testProcessWhenEmailOTPIsOptionalWithFederatedEmail() throws Exception {

        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "false");
        parameters.put(EmailOTPAuthenticatorConstants.SEND_OTP_TO_FEDERATED_EMAIL_ATTRIBUTE, "true");
        parameters.put(EmailOTPAuthenticatorConstants.FEDERATED_EMAIL_ATTRIBUTE_KEY, "email");
        parameters.put(EmailOTPAuthenticatorConstants.EMAIL_API, "EmailAPI");
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        context.setSubject(authenticatedUser);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        context.setAuthenticatorProperties(parameters);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(userRealm);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(anyString(), anyString(), anyString()))
                .thenReturn(null);
        setStepConfigWithFederatedAuthenticator(authenticatedUser, authenticatorConfig);
        mockFederatedEmailAttributeKey(parameters, authenticatedUser, EmailOTPAuthenticatorTestConstants.EMAIL_ADDRESS);
        // Mocking the random number generation since algorithm DRBG is not supported in java 8. Revert this when
        // source is compatible with java 11.
        when(OneTimePassword.getRandomNumber(EmailOTPAuthenticatorConstants.SECRET_KEY_LENGTH)).thenReturn("123456");
        emailOTPAuthenticator = PowerMockito.spy(new EmailOTPAuthenticator());
        doNothing().when(emailOTPAuthenticator, "sendOTP", anyString(), anyString(), anyString(), anyObject(),
                anyString());
        AuthenticatorFlowStatus status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @Test(description = "Test case for process() method when email OTP is mandatory for federated user and email " +
            "attribute is not available.", expectedExceptions = AuthenticationFailedException.class)
    public void testProcessWhenEmailOTPIsMandatoryWithoutFederatedEmail() throws AuthenticationFailedException,
            LogoutFailedException, UserStoreException {

        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "true");
        parameters.put(EmailOTPAuthenticatorConstants.SEND_OTP_TO_FEDERATED_EMAIL_ATTRIBUTE, "true");
        parameters.put(EmailOTPAuthenticatorConstants.EMAIL_API, "EmailAPI");
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        context.setSubject(authenticatedUser);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        context.setAuthenticatorProperties(parameters);
        when(FederatedAuthenticatorUtil.isUserExistInUserStore(anyString())).thenReturn(true);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(anyString(), anyString(), anyString()))
                .thenReturn(null);
        setStepConfigWithFederatedAuthenticator(authenticatedUser, authenticatorConfig);
        mockFederatedEmailAttributeKey(parameters, authenticatedUser, EMAIL_ADDRESS);
        emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
    }

    @Test(description = "Test case for process() method when email OTP is optional and federated email attribute is " +
            "not available.")
    public void testProcessWhenEmailOTPIsOptionalWithoutFederatedEmail() throws AuthenticationFailedException,
            LogoutFailedException, UserStoreException {

        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "false");
        parameters.put(EmailOTPAuthenticatorConstants.SEND_OTP_TO_FEDERATED_EMAIL_ATTRIBUTE, "true");
        parameters.put(EmailOTPAuthenticatorConstants.EMAIL_API, "EmailAPI");
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        context.setSubject(authenticatedUser);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        context.setAuthenticatorProperties(parameters);
        when(FederatedAuthenticatorUtil.isUserExistInUserStore(anyString())).thenReturn(true);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(userRealm);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(anyString(), anyString(), anyString()))
                .thenReturn(null);
        setStepConfigWithFederatedAuthenticator(authenticatedUser, authenticatorConfig);
        mockFederatedEmailAttributeKey(parameters, authenticatedUser, EMAIL_ADDRESS);
        AuthenticatorFlowStatus status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.SUCCESS_COMPLETED);
    }

    @Test(description = "Test case for process() method when email OTP is Mandatory and send OTP to federated " +
            "email attribute is diabled.", expectedExceptions = AuthenticationFailedException.class)
    public void testProcessWhenEmailOTPIsMandatoryWithoutSendOTPToFederatedEmail() throws AuthenticationFailedException,
            LogoutFailedException, UserStoreException {

        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "true");
        parameters.put(EmailOTPAuthenticatorConstants.SEND_OTP_TO_FEDERATED_EMAIL_ATTRIBUTE, "false");
        parameters.put(EmailOTPAuthenticatorConstants.FEDERATED_EMAIL_ATTRIBUTE_KEY, "email");
        parameters.put(EmailOTPAuthenticatorConstants.EMAIL_API, "EmailAPI");
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        context.setSubject(authenticatedUser);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        context.setAuthenticatorProperties(parameters);
        when(FederatedAuthenticatorUtil.isUserExistInUserStore(anyString())).thenReturn(true);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(anyString(), anyString(), anyString()))
                .thenReturn(null);
        setStepConfigWithFederatedAuthenticator(authenticatedUser, authenticatorConfig);
        mockFederatedEmailAttributeKey(parameters, authenticatedUser, EMAIL_ADDRESS);
        emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
    }

    @Test(description = "Test case for process() method when email OTP is Optional and send OTP to federated " +
            "email attribute is diabled.")
    public void testProcessWhenEmailOTPIsOptionalWithoutSendOTPToFederatedEmail() throws AuthenticationFailedException,
            LogoutFailedException, UserStoreException {

        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EmailOTPAuthenticatorConstants.IS_EMAILOTP_MANDATORY, "false");
        parameters.put(EmailOTPAuthenticatorConstants.SEND_OTP_TO_FEDERATED_EMAIL_ATTRIBUTE, "false");
        parameters.put(EmailOTPAuthenticatorConstants.FEDERATED_EMAIL_ATTRIBUTE_KEY, "email");
        parameters.put(EmailOTPAuthenticatorConstants.EMAIL_API, "EmailAPI");
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        context.setSubject(authenticatedUser);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        context.setAuthenticatorProperties(parameters);
        when(FederatedAuthenticatorUtil.isUserExistInUserStore(anyString())).thenReturn(true);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(userRealm);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(anyString(), anyString(), anyString()))
                .thenReturn(null);
        setStepConfigWithFederatedAuthenticator(authenticatedUser, authenticatorConfig);
        mockFederatedEmailAttributeKey(parameters, authenticatedUser, EMAIL_ADDRESS);
        AuthenticatorFlowStatus status = emailOTPAuthenticator.process(httpServletRequest, httpServletResponse,
                context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.SUCCESS_COMPLETED);
    }

    @Test
    public void testGetPrepareURLParams() throws Exception {
        String api = "gmail";
        String urlParams = "send=true";
        Map<String, String> parameters = new HashMap<>();
        parameters.put(api + EmailOTPAuthenticatorConstants.URL_PARAMS, urlParams);
        //get from context
        context.setTenantDomain(TENANT_DOMAIN);
        context.setProperty(api + EmailOTPAuthenticatorConstants.URL_PARAMS, urlParams);
        Assert.assertEquals(Whitebox.invokeMethod(emailOTPAuthenticator, "getPrepareURLParams",
                context, parameters, api), urlParams);
        //get from parameters
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        Assert.assertEquals(Whitebox.invokeMethod(emailOTPAuthenticator, "getPrepareURLParams",
                context, parameters, api), urlParams);
    }

    @Test
    public void testGetPrepareFormData() throws Exception {
        String api = "gmail";
        String formData = "accessToken=asdf";
        Map<String, String> parameters = new HashMap<>();
        parameters.put(api + EmailOTPAuthenticatorConstants.FORM_DATA, formData);
        //get from context
        context.setTenantDomain(TENANT_DOMAIN);
        context.setProperty(api + EmailOTPAuthenticatorConstants.FORM_DATA, formData);
        Assert.assertEquals(Whitebox.invokeMethod(emailOTPAuthenticator, "getPrepareFormData",
                context, parameters, api), formData);
        //get from parameters
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        Assert.assertEquals(Whitebox.invokeMethod(emailOTPAuthenticator, "getPrepareFormData",
                context, parameters, api), formData);
    }

    @Test
    public void testGetFailureString() throws Exception {
        String api = "gmail";
        String failureString = "Authentication Failed";
        Map<String, String> parameters = new HashMap<>();
        parameters.put(api + EmailOTPAuthenticatorConstants.FAILURE, failureString);
        //get from context
        context.setTenantDomain(TENANT_DOMAIN);
        context.setProperty(api + EmailOTPAuthenticatorConstants.FAILURE, failureString);
        Assert.assertEquals(Whitebox.invokeMethod(emailOTPAuthenticator, "getFailureString",
                context, parameters, api), failureString);
        //get from parameters
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        Assert.assertEquals(Whitebox.invokeMethod(emailOTPAuthenticator, "getFailureString",
                context, parameters, api), failureString);
    }

    @Test
    public void testGetAuthTokenType() throws Exception {
        String api = "gmail";
        String tokenType = "Oauth2";
        Map<String, String> parameters = new HashMap<>();
        parameters.put(api + EmailOTPAuthenticatorConstants.HTTP_AUTH_TOKEN_TYPE, tokenType);
        //get from context
        context.setTenantDomain(TENANT_DOMAIN);
        context.setProperty(api + EmailOTPAuthenticatorConstants.HTTP_AUTH_TOKEN_TYPE, tokenType);
        Assert.assertEquals(Whitebox.invokeMethod(emailOTPAuthenticator, "getAuthTokenType",
                context, parameters, api), tokenType);
        //get from parameters
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        Assert.assertEquals(Whitebox.invokeMethod(emailOTPAuthenticator, "getAuthTokenType",
                context, parameters, api), tokenType);
    }

    @Test
    public void testGetAccessTokenEndpoint() throws Exception {
        String api = "gmail";
        String tokenEndpoint = "api/v4/oauth2/token";
        Map<String, String> parameters = new HashMap<>();
        parameters.put(api + EmailOTPAuthenticatorConstants.EMAILOTP_TOKEN_ENDPOINT, tokenEndpoint);
        //get from context
        context.setTenantDomain(TENANT_DOMAIN);
        context.setProperty(api + EmailOTPAuthenticatorConstants.EMAILOTP_TOKEN_ENDPOINT, tokenEndpoint);
        Assert.assertEquals(Whitebox.invokeMethod(emailOTPAuthenticator, "getAccessTokenEndpoint",
                context, parameters, api), tokenEndpoint);
        //get from parameters
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        Assert.assertEquals(Whitebox.invokeMethod(emailOTPAuthenticator, "getAccessTokenEndpoint",
                context, parameters, api), tokenEndpoint);
    }

    @Test
    public void testGetAPI() throws Exception {
        Map<String, String> authenticatorProperties = new HashMap<>();
        authenticatorProperties.put(EmailOTPAuthenticatorConstants.EMAIL_API, "EmailAPI");
        Assert.assertEquals(Whitebox.invokeMethod(emailOTPAuthenticator, "getAPI",
                authenticatorProperties), EmailOTPAuthenticatorConstants.EMAIL_API);
    }

    @Test
    public void testIsShowEmailAddressInUIEnable() throws Exception {
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        Map<String, String> parametersMap = new HashMap<>();
        parametersMap.put(EmailOTPAuthenticatorConstants.SHOW_EMAIL_ADDRESS_IN_UI, "true");
        Assert.assertTrue(Whitebox.invokeMethod(emailOTPAuthenticator, "isShowEmailAddressInUIEnable",
                context, parametersMap));
    }

    @Test
    public void testIsShowEmailAddressInUIEnableForTenant() throws Exception {
        context.setTenantDomain(TENANT_DOMAIN);
        context.setProperty(EmailOTPAuthenticatorConstants.SHOW_EMAIL_ADDRESS_IN_UI, "false");
        Assert.assertFalse(Whitebox.invokeMethod(emailOTPAuthenticator, "isShowEmailAddressInUIEnable",
                context, null));
    }

    @Test
    public void testisEmailAddressUpdateEnable() throws Exception {
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        Map<String, String> parametersMap = new HashMap<>();
        parametersMap.put(EmailOTPAuthenticatorConstants.IS_ENABLE_EMAIL_VALUE_UPDATE, "true");
        Assert.assertTrue(Whitebox.invokeMethod(emailOTPAuthenticator, "isEmailAddressUpdateEnable",
                context, parametersMap));
    }

    @Test
    public void testisEmailAddressUpdateEnableForTenant() throws Exception {
        context.setTenantDomain(TENANT_DOMAIN);
        context.setProperty(EmailOTPAuthenticatorConstants.IS_ENABLE_EMAIL_VALUE_UPDATE, "false");
        Assert.assertFalse(Whitebox.invokeMethod(emailOTPAuthenticator, "isEmailAddressUpdateEnable",
                context, null));
    }

    @Test(expectedExceptions = {AuthenticationFailedException.class})
    public void testUpdateUserAttributeWithNullUserRealm() throws Exception {
        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(null);
        Whitebox.invokeMethod(emailOTPAuthenticator, "updateUserAttribute",
                USER_NAME, new HashMap<>(),
                TENANT_DOMAIN);
    }

    @Test(expectedExceptions = {AuthenticationFailedException.class})
    public void testUpdateUserAttributeWithUserStoreException() throws Exception {
        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(EmailOTPAuthenticatorTestConstants.TENANT_ID);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        Whitebox.invokeMethod(emailOTPAuthenticator, "updateUserAttribute",
                USER_NAME, new HashMap<>(),
                TENANT_DOMAIN);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void testCheckEmailOTPBehaviour() throws Exception {
        String api = "gmail";
        String mailingEndpoint = "api/v1/sendMail";
        Map<String, String> emailOTPParameters = new HashMap<>();
        Map<String, String> authenticatorProperties = new HashMap<>();
        emailOTPParameters.put(api + EmailOTPAuthenticatorConstants.MAILING_ENDPOINT, mailingEndpoint);
        emailOTPParameters.put(api + EmailOTPAuthenticatorConstants.EMAILOTP_API_KEY, "apiKey");
        emailOTPParameters.put(api + EmailOTPAuthenticatorConstants.REFRESH_TOKEN, "refreshToken");
        emailOTPParameters.put(api + EmailOTPAuthenticatorConstants.CLIENT_ID, "clientId");
        emailOTPParameters.put(api + EmailOTPAuthenticatorConstants.CLIENT_SECRET, "clientSecret");
        authenticatorProperties.put(EmailOTPAuthenticatorConstants.EMAIL_API, api);
        authenticatorProperties.put(EmailOTPAuthenticatorConstants.EMAILOTP_EMAIL,
                EMAIL_ADDRESS);
        context.setTenantDomain(EmailOTPAuthenticatorConstants.SUPER_TENANT);
        Whitebox.invokeMethod(emailOTPAuthenticator, "checkEmailOTPBehaviour", context,
                emailOTPParameters, authenticatorProperties, EMAIL_ADDRESS,
                USER_NAME, "123456", EmailOTPAuthenticatorConstants.IP_ADDRESS);
    }

    /**
     * Mock the federated attribute key.
     *
     * @param parameters        paramters map.
     * @param authenticatedUser authenticated user.
     * @param emailAddress      email address of the user.
     */
    private void mockFederatedEmailAttributeKey(Map<String, String> parameters, AuthenticatedUser authenticatedUser,
                                                String emailAddress) {
        Map<ClaimMapping, String> userClaims = new HashMap<>();
        userClaims.put(ClaimMapping.build("email", null, null, false),
                emailAddress);
        authenticatedUser.setUserAttributes(userClaims);

        Map<String, AuthenticatedIdPData> authenticatedIdPs = new HashMap<>();

        AuthenticatedIdPData authenticatedIdPData = new AuthenticatedIdPData();
        authenticatedIdPData.setUser(authUser);
        when(authUser.getUserAttributes()).thenReturn(userClaims);

        when(context.getCurrentAuthenticatedIdPs()).thenReturn(authenticatedIdPs);
        when(FederatedAuthenticatorUtil.getAuthenticatorConfig(EmailOTPAuthenticatorConstants.AUTHENTICATOR_NAME))
                .thenReturn(parameters);
    }

    /**
     * Set a step configuration to the context with local authenticator and email OTP authenticator.
     *
     * @param authenticatedUser   {@link AuthenticatedUser} object
     * @param authenticatorConfig {@link AuthenticatorConfig} object
     */
    private void setStepConfigWithBasicAuthenticator(AuthenticatedUser authenticatedUser,
                                                     AuthenticatorConfig authenticatorConfig) {

        Map<Integer, StepConfig> stepConfigMap = new HashMap<>();
        StepConfig stepConfig = new StepConfig();
        stepConfig.setAuthenticatedUser(authenticatedUser);
        stepConfig.setSubjectAttributeStep(true);
        stepConfig.setAuthenticatedIdP("LOCAL");
        AuthenticatorConfig localAuthenticatorConfig = new AuthenticatorConfig();
        localAuthenticatorConfig.setName("BasicAuthenticator");
        when(localApplicationAuthenticator.getName()).thenReturn("BasicAuthenticator");
        localAuthenticatorConfig.setApplicationAuthenticator(localApplicationAuthenticator);
        stepConfig.setAuthenticatedAutenticator(localAuthenticatorConfig);
        stepConfigMap.put(1, stepConfig);

        // Email OTP authenticator step
        StepConfig emailOTPStep = new StepConfig();
        authenticatorConfig.setName(EmailOTPAuthenticatorConstants.AUTHENTICATOR_NAME);
        List<AuthenticatorConfig> authenticatorList = new ArrayList<>();
        authenticatorList.add(authenticatorConfig);
        emailOTPStep.setAuthenticatorList(authenticatorList);
        stepConfigMap.put(2, emailOTPStep);

        SequenceConfig sequenceConfig = new SequenceConfig();
        sequenceConfig.setStepMap(stepConfigMap);
        context.setSequenceConfig(sequenceConfig);
        context.setCurrentStep(2);
    }

    /**
     * Set a step configuration to the context with federated authenticator and email OTP authenticator.
     *
     * @param authenticatedUser   {@link AuthenticatedUser} object
     * @param authenticatorConfig {@link AuthenticatorConfig} object
     */
    private void setStepConfigWithFederatedAuthenticator(AuthenticatedUser authenticatedUser,
                                                         AuthenticatorConfig authenticatorConfig) {

        Map<Integer, StepConfig> stepConfigMap = new HashMap<>();
        StepConfig stepConfig = new StepConfig();
        stepConfig.setSubjectAttributeStep(true);
        Map<ClaimMapping, String> userClaims = new HashMap<>();
        userClaims.put(ClaimMapping.build("email", null, null, false),
                EMAIL_ADDRESS);
        authenticatedUser.setUserAttributes(userClaims);
        authenticatedUser.setFederatedUser(true);
        stepConfig.setAuthenticatedUser(authenticatedUser);
        stepConfig.setAuthenticatedIdP("FEDERATED");
        AuthenticatorConfig federatedAuthenticatorConfig = new AuthenticatorConfig();
        stepConfig.setAuthenticatedAutenticator(federatedAuthenticatorConfig);
        stepConfigMap.put(1, stepConfig);

        // Email OTP authenticator step
        StepConfig emailOTPStep = new StepConfig();
        authenticatorConfig.setName(EmailOTPAuthenticatorConstants.AUTHENTICATOR_NAME);
        List<AuthenticatorConfig> authenticatorList = new ArrayList<>();
        authenticatorList.add(authenticatorConfig);
        emailOTPStep.setAuthenticatorList(authenticatorList);
        stepConfigMap.put(2, emailOTPStep);

        SequenceConfig sequenceConfig = new SequenceConfig();
        sequenceConfig.setStepMap(stepConfigMap);
        context.setSequenceConfig(sequenceConfig);
        context.setCurrentStep(2);
    }

    @Test
    public void testProcessAuthenticationResponseWithvalidBackupCode() throws Exception {

        when(httpServletRequest.getParameter(EmailOTPAuthenticatorConstants.CODE)).thenReturn("123456");
        context.setProperty(EmailOTPAuthenticatorConstants.OTP_TOKEN, "123");
        context.setProperty(EmailOTPAuthenticatorConstants.USER_NAME, USER_NAME);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(USER_NAME);
        authenticatedUser.setUserName(USER_NAME);
        setStepConfigWithBasicAuthenticator(authenticatedUser, authenticatorConfig);
        when((AuthenticatedUser) context.getProperty(EmailOTPAuthenticatorConstants.AUTHENTICATED_USER)).
                thenReturn(authenticatedUser);
        when(EmailOTPUtils.getConfiguration(context, EmailOTPAuthenticatorConstants.BACKUP_CODE)).thenReturn("true");
        when(context.getProperty(EmailOTPAuthenticatorConstants.OTP_GENERATED_TIME)).thenReturn(anyLong());
        when(IdentityTenantUtil.getTenantId("carbon.super")).thenReturn(-1234);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        mockUserRealm();
        when(MultitenantUtils.getTenantAwareUsername(USER_NAME))
                .thenReturn(USER_NAME);
        when(userStoreManager.getUserClaimValue(USER_NAME,
                EmailOTPAuthenticatorConstants.OTP_BACKUP_CODES_CLAIM, null)).thenReturn("123456,789123");
        when(userStoreManager.getClaimManager()).thenReturn(claimManager);
        when(userStoreManager.getClaimManager().getClaim(EmailOTPAuthenticatorConstants.OTP_BACKUP_CODES_CLAIM))
                .thenReturn(claim);
        when(context.getProperty(EmailOTPAuthenticatorConstants.CODE_MISMATCH)).thenReturn(false);
        Whitebox.invokeMethod(emailOTPAuthenticator, "processAuthenticationResponse",
                httpServletRequest, httpServletResponse, context);
    }

    private void mockServiceURLBuilder() throws URLBuilderException {

        ServiceURLBuilder builder = new ServiceURLBuilder() {

            String path = "";

            @Override
            public ServiceURLBuilder addPath(String... strings) {

                Arrays.stream(strings).forEach(x -> {
                    path += "/" + x;
                });
                return this;
            }

            @Override
            public ServiceURLBuilder addParameter(String s, String s1) {

                return this;
            }

            @Override
            public ServiceURLBuilder setFragment(String s) {

                return this;
            }

            @Override
            public ServiceURLBuilder addFragmentParameter(String s, String s1) {

                return this;
            }

            @Override
            public ServiceURL build() throws URLBuilderException {

                ServiceURL serviceURL = mock(ServiceURL.class);
                PowerMockito.when(serviceURL.getAbsolutePublicURL()).thenReturn("https://localhost:9443" + path);
                PowerMockito.when(serviceURL.getRelativePublicURL()).thenReturn(path);
                PowerMockito.when(serviceURL.getRelativeInternalURL()).thenReturn(path);
                return serviceURL;
            }
        };

        mockStatic(ServiceURLBuilder.class);
        PowerMockito.when(ServiceURLBuilder.create()).thenReturn(builder);
    }

    @ObjectFactory
    public IObjectFactory getObjectFactory() {
        return new PowerMockObjectFactory();
    }

    /**
     * Set a step configuration to the context with EmailOTP authenticator.
     *
     * @param authenticatorConfig object
     * @param context             object
     */
    private void setStepConfigWithEmailOTPAuthenticator(AuthenticatorConfig authenticatorConfig, AuthenticationContext context) {

        Map<Integer, StepConfig> stepConfigMap = new HashMap<>();
        // Email OTP authenticator step.
        StepConfig emailOTPStep = new StepConfig();
        authenticatorConfig.setName(EmailOTPAuthenticatorConstants.AUTHENTICATOR_NAME);
        List<AuthenticatorConfig> authenticatorList = new ArrayList<>();
        authenticatorList.add(authenticatorConfig);
        emailOTPStep.setAuthenticatorList(authenticatorList);
        emailOTPStep.setSubjectAttributeStep(true);
        stepConfigMap.put(1, emailOTPStep);

        SequenceConfig sequenceConfig = new SequenceConfig();
        sequenceConfig.setStepMap(stepConfigMap);
        context.setSequenceConfig(sequenceConfig);
        context.setCurrentStep(1);
    }
}
