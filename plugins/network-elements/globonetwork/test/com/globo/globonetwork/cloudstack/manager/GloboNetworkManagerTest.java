/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.globo.globonetwork.cloudstack.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.cloud.network.dao.LoadBalancerPortMapDao;
import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.lb.LoadBalancingRule;
import org.apache.cloudstack.acl.ControlledEntity.ACLType;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.region.PortableIpDao;
import org.apache.cloudstack.region.PortableIpRangeDao;
import org.apache.cloudstack.test.utils.SpringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.owasp.esapi.waf.ConfigurationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.configuration.ConfigurationManager;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.dc.dao.VlanDao;
import com.cloud.domain.dao.DomainDao;
import com.cloud.exception.CloudException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.IpAddressManager;
import com.cloud.network.Network.Provider;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkService;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.UserIpv6AddressDao;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkServiceMapDao;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.network.lb.LoadBalancingRulesService;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.projects.ProjectManager;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ServerResource;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.DomainManager;
import com.cloud.user.UserVO;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.globo.globodns.cloudstack.element.GloboDnsElementService;
import com.globo.globonetwork.cloudstack.GloboNetworkEnvironmentVO;
import com.globo.globonetwork.cloudstack.commands.CreateNewVlanInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.DeallocateVlanFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkEnvironmentDao;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkIpDetailDao;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkLoadBalancerEnvironmentDAO;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkNetworkDao;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkVipAccDao;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVlanResponse;

public class GloboNetworkManagerTest {

    private static long zoneId = 5L;
    private static long networkOfferingId = 10L;
    private static long globoNetworkEnvironmentId = 120L;
    private static long physicalNetworkId = 200L;
    private static long globoNetworkHostId = 7L;
    private static long domainId = 10L;
    private AccountVO acct = null;
    private UserVO user = null;

    GloboNetworkManager _globoNetworkService;

    @Mock
    DataCenterDao _dcDao;

    @Mock
    PhysicalNetworkDao _physicalNetworkDao;

    @Mock
    GloboNetworkEnvironmentDao _globoNetworkEnvironmentDao;

    @Mock
    HostDao _hostDao;

    @Mock
    ConfigurationDao _configDao;

    @Mock
    AgentManager _agentMgr;

    @Mock
    ResourceManager _resourceMgr;

    @Mock
    AccountManager _acctMgr;

    @BeforeClass
    public static void setUp() throws ConfigurationException {
    }

    @Before
    public void testSetUp() {
        MockitoAnnotations.initMocks(this);

        _globoNetworkService = new GloboNetworkManager();
        acct = new AccountVO(200L);
        acct.setType(Account.ACCOUNT_TYPE_NORMAL);
        acct.setAccountName("user");
        acct.setDomainId(domainId);

        user = new UserVO();
        user.setUsername("user");
        user.setAccountId(acct.getAccountId());

        _globoNetworkService._dcDao = _dcDao;
        _globoNetworkService._physicalNetworkDao = _physicalNetworkDao;
        _globoNetworkService._globoNetworkEnvironmentDao = _globoNetworkEnvironmentDao;
        _globoNetworkService._hostDao = _hostDao;
        _globoNetworkService._configDao = _configDao;
        _globoNetworkService._agentMgr = _agentMgr;
        _globoNetworkService._resourceMgr = _resourceMgr;
        _globoNetworkService._accountMgr = _acctMgr;

        CallContext.register(user, acct);
        when(_acctMgr.getSystemAccount()).thenReturn(this.acct);
        when(_acctMgr.getSystemUser()).thenReturn(this.user);
    }

    @After
    public void testTearDown() {
        CallContext.unregister();
        acct = null;
    }

    @Test
    public void revertGloboNetworkCreationWhenFailureNetworkCreation() throws CloudException {

        DataCenterVO dc = new DataCenterVO(0L, null, null, null, null, null, null, null, null, null, null, null, null);
        when(_dcDao.findById(anyLong())).thenReturn(dc);

        List<PhysicalNetworkVO> pNtwList = new ArrayList<PhysicalNetworkVO>();
        pNtwList.add(new PhysicalNetworkVO(physicalNetworkId, zoneId, null, null, null, null, null));
        when(_physicalNetworkDao.listByZone(zoneId)).thenReturn(pNtwList);
        String networkName = "MockTestNetwork";
        when(_globoNetworkEnvironmentDao.findByPhysicalNetworkIdAndEnvironmentId(physicalNetworkId, globoNetworkEnvironmentId)).thenReturn(
                new GloboNetworkEnvironmentVO(physicalNetworkId, networkName, globoNetworkEnvironmentId));

        HostVO napiHost = new HostVO(globoNetworkHostId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, zoneId, null, 0L, 0L,
                null, null, null, 0L, null);
        when(_hostDao.findByTypeNameAndZoneId(zoneId, Provider.GloboNetwork.getName(), Host.Type.L2Networking)).thenReturn(napiHost);

        Answer answer = new GloboNetworkVlanResponse(new CreateNewVlanInGloboNetworkCommand(), null, null, null, null, null, null, null, false, null, false);
        when(_agentMgr.easySend(eq(globoNetworkHostId), any(CreateNewVlanInGloboNetworkCommand.class))).thenReturn(answer);

        when(_physicalNetworkDao.findById(physicalNetworkId)).thenReturn(null);

        try {
            _globoNetworkService.createNetwork(networkName, networkName, zoneId, networkOfferingId, globoNetworkEnvironmentId, null, ACLType.Domain, null, null, null, null, true,
                    null, false);
            // This command must throw InvalidParameterValueException, otherwise fails
            Assert.fail();
        } catch (ResourceAllocationException e) {
            verify(_agentMgr, atLeastOnce()).easySend(eq(globoNetworkHostId), any(DeallocateVlanFromGloboNetworkCommand.class));
        }
    }

    @Test
    public void checkPermissionsBeforeCreatingVlanOnGloboNetwork() throws CloudException {
        try {
            when(_acctMgr.finalizeOwner(eq(acct), eq(acct.getAccountName()), eq(domainId), anyLong())).thenThrow(new PermissionDeniedException(""));

            acct.setDomainId(domainId + 1);
            _globoNetworkService.createNetwork("net-name", "display-name", zoneId, networkOfferingId, globoNetworkEnvironmentId, null, ACLType.Domain, acct.getAccountName(), null,
                    domainId, null, true, null, false);
            fail();
        } catch (PermissionDeniedException e) {
            verify(_agentMgr, never()).easySend(any(Long.class), any(Command.class));
        }
    }

    @Test(expected = InvalidParameterValueException.class)
    public void addGloboNetworkHostInvalidParameters() throws CloudException {

        String username = null;
        String password = null;
        String url = null;

        CallContext.register(user, acct);

        _globoNetworkService.addGloboNetworkHost(physicalNetworkId, username, password, url);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void addGloboNetworkHostEmptyParameters() throws CloudException {

        String username = "";
        String password = "";
        String url = "";

        CallContext.register(user, acct);

        _globoNetworkService.addGloboNetworkHost(physicalNetworkId, username, password, url);
    }

    @Test
    public void addGloboNetworkHost() throws CloudException {

        String username = "testUser";
        String password = "testPwd";
        String url = "testUrl";

        PhysicalNetworkVO pNtwk = new PhysicalNetworkVO(physicalNetworkId, zoneId, null, null, null, null, null);
        when(_physicalNetworkDao.findById(physicalNetworkId)).thenReturn(pNtwk);

        HostVO globoNetworkHost = new HostVO(1L, "GloboNetwork", null, "Up", "L2Networking", "", null, null, "", null, null, null, null, null, null, null, null, zoneId, null, 0L,
                0L, null, null, null, 0L, null);

        when(_resourceMgr.addHost(eq(zoneId), any(ServerResource.class), eq(Host.Type.L2Networking), anyMapOf(String.class, String.class))).thenReturn(globoNetworkHost);

        TransactionLegacy tx = TransactionLegacy.open(TransactionLegacy.CLOUD_DB);
        try {
            CallContext.register(user, acct);

            Host host = _globoNetworkService.addGloboNetworkHost(physicalNetworkId, username, password, url);
            assertNotNull(host);
            assertEquals(host.getDataCenterId(), zoneId);
            assertEquals(host.getName(), "GloboNetwork");
        } finally {
            tx.rollback();
        }
    }

    @Test
    public void testLbPortMapValidationWithNoAdditionalPortMap() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        try {
            new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        }catch(Exception e){
            fail();
        }
    }

    @Test
    public void testLbPortMapValidationWithOneAdditionalPortMap() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Arrays.asList(new String[] { "443:8443" }));

        try {
            new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        }catch(Exception e){
            fail();
        }
    }

    @Test
    public void testLbPortMapValidationWithMoreThanOneAdditionalPortMap() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Arrays.asList(new String[] { "443:8443", "22:22" }));

        try {
            new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        }catch(Exception e){
            fail();
        }
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithAdditionalPortMapEqualsMainPortMap() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Arrays.asList(new String[] { "80:8080" }));

        new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        fail();
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithDuplicatedAdditionalPortMap() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Arrays.asList(new String[] { "443:8443", "443:8443" }));

        new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        fail();
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithNoPrivatePort() {
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Arrays.asList(new String[] { "80:" }));

        new GloboNetworkManager().validatePortMaps(loadBalancingRule);
        fail();
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithInvalidPortMapFormat() {
        GloboNetworkManager globoNetworkManager = new GloboNetworkManager();
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Arrays.asList(new String[] { "----" }));

        globoNetworkManager.validatePortMaps(loadBalancingRule);
        fail();
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithEmptyPortMapValue() {
        GloboNetworkManager globoNetworkManager = new GloboNetworkManager();
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Arrays.asList(new String[] { "" }));

        globoNetworkManager.validatePortMaps(loadBalancingRule);
        fail();
    }

    @Test(expected=InvalidParameterValueException.class)
    public void testLbPortMapValidationWithNotNumericValue() {
        GloboNetworkManager globoNetworkManager = new GloboNetworkManager();
        LoadBalancingRule loadBalancingRule = createLoadBalancerRule();
        loadBalancingRule.setAdditionalPortMap(Arrays.asList(new String[] { "abc:abc" }));

        globoNetworkManager.validatePortMaps(loadBalancingRule);
        fail();
    }

    protected LoadBalancingRule createLoadBalancerRule() {
        return new LoadBalancingRule(new LoadBalancerVO("id", "lb", "lb", 1, 80, 8080, "algorithm", 1, 1, 1, "HTTP"), null, null, null, null);
    }

    @Configuration
    @ComponentScan(basePackageClasses = {GloboNetworkManager.class}, includeFilters = {@Filter(value = TestConfiguration.Library.class, type = FilterType.CUSTOM)}, useDefaultFilters = false)
    public static class TestConfiguration extends SpringUtils.CloudStackTestConfiguration {

        @Bean
        public DomainDao domainDao() {
            return mock(DomainDao.class);
        }

        @Bean
        public HostDao hostDao() {
            return mock(HostDao.class);
        }

        @Bean
        public DataCenterDao dataCenterDao() {
            return mock(DataCenterDao.class);
        }

        @Bean
        public HostPodDao hostPodDao() {
            return mock(HostPodDao.class);
        }

        @Bean
        public VlanDao vlanDao() {
            return mock(VlanDao.class);
        }

        @Bean
        public PhysicalNetworkDao physicalNetworkDao() {
            return mock(PhysicalNetworkDao.class);
        }

        @Bean
        public NetworkOfferingDao networkOfferingDao() {
            return mock(NetworkOfferingDao.class);
        }

        @Bean
        public UserDao userDao() {
            return mock(UserDao.class);
        }

        @Bean
        public NetworkDao networkDao() {
            return mock(NetworkDao.class);
        }

        @Bean
        public NetworkServiceMapDao networkServiceMapDao() {
            return mock(NetworkServiceMapDao.class);
        }

        @Bean
        public GloboNetworkNetworkDao globoNetworkNetworkDao() {
            return mock(GloboNetworkNetworkDao.class);
        }

        @Bean
        public GloboNetworkEnvironmentDao globoNetworkEnvironmentDao() {
            return mock(GloboNetworkEnvironmentDao.class);
        }

        @Bean
        public VMInstanceDao vmDao() {
            return mock(VMInstanceDao.class);
        }

        @Bean
        public NicDao nicDao() {
            return mock(NicDao.class);
        }

        @Bean
        public ConfigurationDao configurationDao() {
            return mock(ConfigurationDao.class);
        }

        @Bean
        public NetworkModel networkModel() {
            return mock(NetworkModel.class);
        }

        @Bean
        public AgentManager agentManager() {
            return mock(AgentManager.class);
        }

        @Bean
        public ConfigurationManager configurationManager() {
            return mock(ConfigurationManager.class);
        }

        @Bean
        public ResourceManager resourceManager() {
            return mock(ResourceManager.class);
        }

        @Bean
        public DomainManager domainManager() {
            return mock(DomainManager.class);
        }

        @Bean
        public NetworkOrchestrationService networkOrchestrationService() {
            return mock(NetworkOrchestrationService.class);
        }

        @Bean
        public AccountManager accountManager() {
            return mock(AccountManager.class);
        }

        @Bean
        public ProjectManager projectManager() {
            return mock(ProjectManager.class);
        }

        @Bean
        public NetworkService networkService() {
            return mock(NetworkService.class);
        }

        @Bean
        public IpAddressManager ipAddressManager() {
            return mock(IpAddressManager.class);
        }

        @Bean
        public PortableIpRangeDao portableDao() {
            return mock(PortableIpRangeDao.class);
        }

        @Bean
        public LoadBalancingRulesManager loadBalancingRulesManager() {
            return mock(LoadBalancingRulesManager.class);
        }

        @Bean
        public GloboNetworkIpDetailDao globoNetworkIpDetailDao() {
            return mock(GloboNetworkIpDetailDao.class);
        }

        @Bean
        public PortableIpDao portableIpDao() {
            return mock(PortableIpDao.class);
        }

        @Bean
        public LoadBalancingRulesService loadBalancingRulesService() {
            return mock(LoadBalancingRulesService.class);
        }

        @Bean
        public GloboDnsElementService globoDnsElementService() {
            return mock(GloboDnsElementService.class);
        }

        @Bean
        public GloboNetworkVipAccDao globoNetworkVipAccDao() {
            return mock(GloboNetworkVipAccDao.class);
        }

        @Bean
        public GloboNetworkLoadBalancerEnvironmentDAO globoNetworkLBEnvironmentDao() {
            return mock(GloboNetworkLoadBalancerEnvironmentDAO.class);
        }

        @Bean
        public IPAddressDao ipAddressDao() {
            return mock(IPAddressDao.class);
        }

        @Bean
        public UserIpv6AddressDao userIpv6AddressDao() {
            return mock(UserIpv6AddressDao.class);
        }

        @Bean
        public LoadBalancerPortMapDao loadBalancerPortMapDao() { return mock(LoadBalancerPortMapDao.class); }

        public static class Library implements TypeFilter {

            @Override
            public boolean match(MetadataReader mdr, MetadataReaderFactory arg1) throws IOException {
                ComponentScan cs = TestConfiguration.class.getAnnotation(ComponentScan.class);
                return SpringUtils.includedInBasePackageClasses(mdr.getClassMetadata().getClassName(), cs);
            }
        }
    }
}
