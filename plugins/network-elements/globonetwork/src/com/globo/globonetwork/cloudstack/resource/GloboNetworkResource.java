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
package com.globo.globonetwork.cloudstack.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.agent.IAgentControl;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.MaintainAnswer;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.network.rules.FirewallRule;
import com.cloud.resource.ServerResource;
import com.cloud.utils.component.ManagerBase;
import com.globo.globonetwork.client.exception.GloboNetworkErrorCodeException;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.http.HttpXMLRequestProcessor;
import com.globo.globonetwork.client.model.Environment;
import com.globo.globonetwork.client.model.Equipment;
import com.globo.globonetwork.client.model.Ip;
import com.globo.globonetwork.client.model.Network;
import com.globo.globonetwork.client.model.Real.RealIP;
import com.globo.globonetwork.client.model.Vip;
import com.globo.globonetwork.client.model.VipEnvironment;
import com.globo.globonetwork.client.model.Vlan;
import com.globo.globonetwork.cloudstack.commands.AcquireNewIpForLbCommand;
import com.globo.globonetwork.cloudstack.commands.ActivateNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.AddAndEnableRealInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.AddOrRemoveVipInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.CreateNewVlanInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.DeallocateVlanFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.DisableAndRemoveRealInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GenerateUrlForEditingVipCommand;
import com.globo.globonetwork.cloudstack.commands.GetNetworkFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetVipInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetVlanInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GloboNetworkErrorAnswer;
import com.globo.globonetwork.cloudstack.commands.ListAllEnvironmentsFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RegisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.ReleaseIpFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveNetworkInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveVipFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.UnregisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAllEnvironmentResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAndIPResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse.Real;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVlanResponse;

public class GloboNetworkResource extends ManagerBase implements ServerResource {
    private String _zoneId;

    private String _guid;

    private String _name;

    private String _username;

    private String _url;

    private String _password;

    protected HttpXMLRequestProcessor _globoNetworkApi;

    private static final Logger s_logger = Logger.getLogger(GloboNetworkResource.class);

    private static final long NETWORK_TYPE = 6; // Rede invalida de equipamentos

    private static final Long EQUIPMENT_TYPE = 10L;

    private enum LbAlgorithm {
        RoundRobin("round-robin"), LeastConn("least-conn");

        String globoNetworkBalMethod;

        LbAlgorithm(String globoNetworkBalMethod) {
            this.globoNetworkBalMethod = globoNetworkBalMethod;
        }

        public String getGloboNetworkBalMethod() {
            return globoNetworkBalMethod;
        }
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {

        try {
            _zoneId = (String)params.get("zoneId");
            if (_zoneId == null) {
                throw new ConfigurationException("Unable to find zone");
            }

            _guid = (String)params.get("guid");
            if (_guid == null) {
                throw new ConfigurationException("Unable to find guid");
            }

            _name = (String)params.get("name");
            if (_name == null) {
                throw new ConfigurationException("Unable to find name");
            }

            _url = (String)params.get("url");
            if (_url == null) {
                throw new ConfigurationException("Unable to find url");
            }

            _username = (String)params.get("username");
            if (_username == null) {
                throw new ConfigurationException("Unable to find username");
            }

            _password = (String)params.get("password");
            if (_password == null) {
                throw new ConfigurationException("Unable to find password");
            }

            _globoNetworkApi = new HttpXMLRequestProcessor(_url, _username, _password);

            if (params.containsKey("readTimeout")) {
                _globoNetworkApi.setReadTimeout(Integer.valueOf((String)params.get("readTimeout")));
            }

            if (params.containsKey("connectTimeout")) {
                _globoNetworkApi.setConnectTimeout(Integer.valueOf((String)params.get("connectTimeout")));
            }

            if (params.containsKey("numberOfRetries")) {
                _globoNetworkApi.setNumberOfRetries(Integer.valueOf((String)params.get("numberOfRetries")));
            }

            return true;
        } catch (NumberFormatException e) {
            s_logger.error("Invalid number in configuration parameters", e);
            throw new ConfigurationException("Invalid number in configuration parameters: " + e);
        }
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public Type getType() {
        return Host.Type.L2Networking;
    }

    @Override
    public StartupCommand[] initialize() {
        StartupCommand cmd = new StartupCommand(getType());
        cmd.setName(_name);
        cmd.setGuid(_guid);
        cmd.setDataCenter(_zoneId);
        cmd.setPod("");
        cmd.setPrivateIpAddress("");
        cmd.setStorageIpAddress("");
        cmd.setVersion(GloboNetworkResource.class.getPackage().getImplementationVersion());
        return new StartupCommand[] {cmd};
    }

    @Override
    public PingCommand getCurrentStatus(long id) {
        return new PingCommand(getType(), id);
    }

    @Override
    public void disconnected() {
    }

    @Override
    public IAgentControl getAgentControl() {
        return null;
    }

    @Override
    public void setAgentControl(IAgentControl agentControl) {
    }

    @Override
    public Answer executeRequest(Command cmd) {
        if (cmd instanceof ReadyCommand) {
            return new ReadyAnswer((ReadyCommand)cmd);
        } else if (cmd instanceof MaintainCommand) {
            return new MaintainAnswer((MaintainCommand)cmd);
        } else if (cmd instanceof GetVlanInfoFromGloboNetworkCommand) {
            return execute((GetVlanInfoFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof CreateNewVlanInGloboNetworkCommand) {
            return execute((CreateNewVlanInGloboNetworkCommand)cmd);
        } else if (cmd instanceof ActivateNetworkCommand) {
            return execute((ActivateNetworkCommand)cmd);
        } else if (cmd instanceof ListAllEnvironmentsFromGloboNetworkCommand) {
            return execute((ListAllEnvironmentsFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof RemoveNetworkInGloboNetworkCommand) {
            return execute((RemoveNetworkInGloboNetworkCommand)cmd);
        } else if (cmd instanceof DeallocateVlanFromGloboNetworkCommand) {
            return execute((DeallocateVlanFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof RegisterEquipmentAndIpInGloboNetworkCommand) {
            return execute((RegisterEquipmentAndIpInGloboNetworkCommand)cmd);
        } else if (cmd instanceof UnregisterEquipmentAndIpInGloboNetworkCommand) {
            return execute((UnregisterEquipmentAndIpInGloboNetworkCommand)cmd);
        } else if (cmd instanceof GetVipInfoFromGloboNetworkCommand) {
            return execute((GetVipInfoFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof AddAndEnableRealInGloboNetworkCommand) {
            return execute((AddAndEnableRealInGloboNetworkCommand)cmd);
        } else if (cmd instanceof DisableAndRemoveRealInGloboNetworkCommand) {
            return execute((DisableAndRemoveRealInGloboNetworkCommand)cmd);
        } else if (cmd instanceof GenerateUrlForEditingVipCommand) {
            return execute((GenerateUrlForEditingVipCommand)cmd);
        } else if (cmd instanceof RemoveVipFromGloboNetworkCommand) {
            return execute((RemoveVipFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof AcquireNewIpForLbCommand) {
            return execute((AcquireNewIpForLbCommand)cmd);
        } else if (cmd instanceof ReleaseIpFromGloboNetworkCommand) {
            return execute((ReleaseIpFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof AddOrRemoveVipInGloboNetworkCommand) {
            return execute((AddOrRemoveVipInGloboNetworkCommand)cmd);
        } else if (cmd instanceof GetNetworkFromGloboNetworkCommand) {
            return execute((GetNetworkFromGloboNetworkCommand)cmd);
        }
        return Answer.createUnsupportedCommandAnswer(cmd);
    }

    private Answer execute(GetNetworkFromGloboNetworkCommand cmd) {
        try {
            if (cmd.getNetworkId() == null) {
                return new Answer(cmd, false, "Invalid network ID");
            }

            Network network = _globoNetworkApi.getNetworkAPI().getNetwork(cmd.getNetworkId(), cmd.isv6());
            if (network == null) {
                return new Answer(cmd, false, "Network with ID " + cmd.getNetworkId() + " not found in GloboNetwork");
            }

            return this.createNetworkResponse(network, cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    private Answer handleGloboNetworkException(Command cmd, GloboNetworkException e) {
        if (e instanceof GloboNetworkErrorCodeException) {
            GloboNetworkErrorCodeException ex = (GloboNetworkErrorCodeException)e;
            s_logger.error("Error accessing GloboNetwork: " + ex.getCode() + " - " + ex.getDescription(), ex);
            return new GloboNetworkErrorAnswer(cmd, ex.getCode(), ex.getDescription());
        } else {
            s_logger.error("Generic error accessing GloboNetwork", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    public Answer execute(AddAndEnableRealInGloboNetworkCommand cmd) {
        try {
            Vip vip = _globoNetworkApi.getVipAPI().getById(cmd.getVipId());
            if (vip == null || !cmd.getVipId().equals(vip.getId())) {
                return new Answer(cmd, false, "Vip request " + cmd.getVipId() + " not found in GloboNetwork");
            }

            Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(cmd.getEquipName());
            if (equipment == null) {
                // Equipment doesn't exist
                return new Answer(cmd, false, "Equipment " + cmd.getEquipName() + " doesn't exist in GloboNetwork");
            }

            List<Ip> ips = _globoNetworkApi.getIpAPI().findIpsByEquipment(equipment.getId());
            Ip ip = null;
            for (Ip equipIp : ips) {
                String equipIpString = equipIp.getIpString();
                if (equipIpString.equals(cmd.getIp())) {
                    ip = equipIp;
                }
            }

            if (ip == null) {
                return new Answer(cmd, false, "IP doesn't exist in this GloboNetwork environment");
            }

            if (!vip.getValidated()) {
                _globoNetworkApi.getVipAPI().validate(cmd.getVipId());
            }

            if (!vip.getCreated()) {
                s_logger.info("Requesting GloboNetwork to create vip " + vip.getId());
                _globoNetworkApi.getVipAPI().create(cmd.getVipId());
            }

            if (vip.getRealsIp() != null) {
                for (RealIP realIp : vip.getRealsIp()) {
                    if (ip.getId().equals(realIp.getIpId())) {
                        // real already added. Only ensure is enabled
                        _globoNetworkApi.getVipAPI().enableReal(cmd.getVipId(), ip.getId(), equipment.getId(), null, null);
                        return new Answer(cmd, true, "Real enabled successfully");
                    }
                }
            }

            // added reals are always enabled by default
            _globoNetworkApi.getVipAPI().addReal(cmd.getVipId(), ip.getId(), equipment.getId(), null, null);
            return new Answer(cmd, true, "Real added and enabled successfully");

        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(DisableAndRemoveRealInGloboNetworkCommand cmd) {
        try {
            Vip vip = _globoNetworkApi.getVipAPI().getById(cmd.getVipId());
            if (vip == null || !cmd.getVipId().equals(vip.getId())) {
                return new Answer(cmd, false, "Vip request " + cmd.getVipId() + " not found in GloboNetwork");
            }

            Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(cmd.getEquipName());
            if (equipment == null) {
                // Equipment doesn't exist. So, there is no Vip either.
                return new Answer(cmd, true, "Equipment " + cmd.getEquipName() + " doesn't exist in GloboNetwork");
            }

            if (vip.getRealsIp() != null) {
                for (RealIP realIp : vip.getRealsIp()) {
                    if (cmd.getIp().equals(realIp.getRealIp())) {
                        // real exists in vip. Remove it.
                        _globoNetworkApi.getVipAPI().removeReal(cmd.getVipId(), realIp.getIpId(), equipment.getId(), realIp.getVipPort(), realIp.getRealPort());
                        return new Answer(cmd, true, "Real removed successfully");
                    }
                }
            }
            return new Answer(cmd, true, "Real not in vipId " + cmd.getVipId());

        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    private Answer execute(RemoveVipFromGloboNetworkCommand cmd) {
        return this.removeVipFromGloboNetwork(cmd, cmd.getVipId(), false);
    }

    private Answer removeVipFromGloboNetwork(Command cmd, Long vipId, boolean keepIp) {
        try {

            Vip vip = _globoNetworkApi.getVipAPI().getById(vipId);

            if (vip == null) {
                return new Answer(cmd, true, "Vip request " + vipId + " was previously removed from GloboNetwork");
            }

            // remove VIP from network device
            if (vip.getCreated()) {
                s_logger.info("Requesting GloboNetwork to remove vip from network device vip_id=" + vip.getId());
                _globoNetworkApi.getVipAPI().removeScriptVip(vipId);
            }

            // remove VIP from GloboNetwork DB
            s_logger.info("Requesting GloboNetwork to remove vip from GloboNetwork DB vip_id=" + vip.getId() + " keepIp=" + keepIp);
            _globoNetworkApi.getVipAPI().removeVip(vipId, keepIp);

            return new Answer(cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(GetVlanInfoFromGloboNetworkCommand cmd) {
        try {
            Vlan vlan = _globoNetworkApi.getVlanAPI().getById(cmd.getVlanId());
            return createResponse(vlan, cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(CreateNewVlanInGloboNetworkCommand cmd) {
        try {
            Vlan vlan = _globoNetworkApi.getVlanAPI().allocateWithoutNetwork(cmd.getGloboNetworkEnvironmentId(), cmd.getVlanName(), cmd.getVlanDescription());

            /*Network network = */_globoNetworkApi.getNetworkAPI().addNetwork(vlan.getId(), Long.valueOf(NETWORK_TYPE), null, cmd.isIpv6());

            // Bug in GloboNetworkApi: I need to have a second call to get networkid
            vlan = _globoNetworkApi.getVlanAPI().getById(vlan.getId());
            return createResponse(vlan, cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ActivateNetworkCommand cmd) {
        try {
            _globoNetworkApi.getNetworkAPI().createNetworks(cmd.getNetworkId(), cmd.getVlanId(), cmd.isv6());
            return new Answer(cmd, true, "Network created");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ListAllEnvironmentsFromGloboNetworkCommand cmd) {
        try {
            List<Environment> apiEnvironmentList = _globoNetworkApi.getEnvironmentAPI().listAll();

            List<GloboNetworkAllEnvironmentResponse.Environment> environmentList = new ArrayList<GloboNetworkAllEnvironmentResponse.Environment>(apiEnvironmentList.size());
            for (Environment apiEnvironment : apiEnvironmentList) {
                GloboNetworkAllEnvironmentResponse.Environment environment = new GloboNetworkAllEnvironmentResponse.Environment();
                environment.setId(apiEnvironment.getId());
                environment.setDcDivisionName(apiEnvironment.getDcDivisionName());
                environment.setL3GroupName(apiEnvironment.getL3GroupName());
                environment.setLogicalEnvironmentName(apiEnvironment.getLogicalEnvironmentName());
                environmentList.add(environment);
            }

            return new GloboNetworkAllEnvironmentResponse(cmd, environmentList);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(RemoveNetworkInGloboNetworkCommand cmd) {
        try {
            _globoNetworkApi.getVlanAPI().remove(cmd.getVlanId());

            return new Answer(cmd, true, "Network removed");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(DeallocateVlanFromGloboNetworkCommand cmd) {
        try {
            _globoNetworkApi.getVlanAPI().deallocate(cmd.getVlanId());
            return new Answer(cmd, true, "Vlan deallocated");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(RegisterEquipmentAndIpInGloboNetworkCommand cmd) {
        try {
            Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(cmd.getVmName());
            if (equipment == null) {
                s_logger.info("Registering virtualmachine " + cmd.getVmName() + " in GloboNetwork");
                // Equipment (VM) does not exist, create it
                equipment = _globoNetworkApi.getEquipmentAPI().insert(cmd.getVmName(), EQUIPMENT_TYPE, cmd.getEquipmentModelId(), cmd.getEquipmentGroupId());
            }

            Vlan vlan = _globoNetworkApi.getVlanAPI().getById(cmd.getVlanId());

            // Make sure this vlan has only one IPv4/IPv6 network associated to it
            if (vlan.getNetworks().size() == 0) {
                return new Answer(cmd, false, "No IPv4/IPv6 networks in this vlan");
            } else if (vlan.getNetworks().size() > 1) {
                return new Answer(cmd, false, "Multiple IPv4/IPv6 networks in this vlan");
            }
            Network network = vlan.getNetworks().get(0);

            Ip ip = _globoNetworkApi.getIpAPI().findByIpAndEnvironment(cmd.getNicIp(), cmd.getEnvironmentId(), network.isv6());
            if (ip == null) {
                // Doesn't exist, create it
                ip = _globoNetworkApi.getIpAPI().saveIp(cmd.getNicIp(), equipment.getId(), cmd.getNicDescription(), network.getId(), network.isv6());
            } else {
                ip = _globoNetworkApi.getIpAPI().getIp(ip.getId(), false);
                if (!ip.getEquipments().contains(cmd.getVmName())) {
                    _globoNetworkApi.getIpAPI().assocIp(ip.getId(), equipment.getId(), network.getId(), network.isv6());
                }
            }

            if (ip == null) {
                return new Answer(cmd, false, "Could not register NIC in GloboNetwork");
            }

            return new Answer(cmd, true, "NIC " + cmd.getNicIp() + " registered successfully in GloboNetwork");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(UnregisterEquipmentAndIpInGloboNetworkCommand cmd) {
        try {
            Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(cmd.getVmName());
            if (equipment == null) {
                s_logger.warn("VM was removed from GloboNetwork before being destroyed in Cloudstack. This is not critical, logging inconsistency: VM UUID " + cmd.getVmName());
                return new Answer(cmd);
            }

            if (cmd.getEnvironmentId() != null && cmd.getNicIp() != null) {
                Ip ip = _globoNetworkApi.getIpAPI().findByIpAndEnvironment(cmd.getNicIp(), cmd.getEnvironmentId(), cmd.isv6());
                if (ip == null) {
                    // Doesn't exist, ignore
                    s_logger.warn("IP was removed from GloboNetwork before being destroyed in Cloudstack. This is not critical, logging inconsistency: IP " + cmd.getNicIp());
                } else {
                    _globoNetworkApi.getEquipmentAPI().removeIP(equipment.getId(), ip.getId(), cmd.isv6());
                }
            }

            // if there are no more IPs in equipment, remove it.
            List<Ip> ipList = _globoNetworkApi.getIpAPI().findIpsByEquipment(equipment.getId());
            if (ipList.size() == 0) {
                _globoNetworkApi.getEquipmentAPI().delete(equipment.getId());
            }

            return new Answer(cmd, true, "NIC " + cmd.getNicIp() + " deregistered successfully in GloboNetwork");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ReleaseIpFromGloboNetworkCommand cmd) {
        try {
            Ip ip = _globoNetworkApi.getIpAPI().checkVipIp(cmd.getIp(), cmd.getVipEnvironmentId(), cmd.isv6());
            if (ip == null) {
                // Doesn't exist, ignore
                s_logger.warn("IP was removed from GloboNetwork before being destroyed in Cloudstack. This is not critical.");
            } else {
                _globoNetworkApi.getIpAPI().deleteIp(ip.getId(), cmd.isv6());
            }
            return new Answer(cmd, true, "IP deleted successfully from GloboNetwork");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(GetVipInfoFromGloboNetworkCommand cmd) {
        try {
            Vip vip = null;
            if (cmd.getVipId() != null) {
                long vipId = cmd.getVipId();
                vip = _globoNetworkApi.getVipAPI().getById(vipId);
            } else {
                Ip ip = _globoNetworkApi.getIpAPI().checkVipIp(cmd.getIp(), cmd.getVipEnvironmentId(), cmd.isv6());
                if (ip != null) {
                    List<Vip> vips = _globoNetworkApi.getVipAPI().getByIp(ip.getIpString());
                    if (!vips.isEmpty()) {
                        vip = vips.get(0);
                    }
                }
            }

            if (vip == null) {
                return new Answer(cmd, false, null);
            }
            return this.createVipResponse(vip, cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(GenerateUrlForEditingVipCommand cmd) {

        try {
            String url = _globoNetworkApi.getVipAPI().generateVipEditingUrl(cmd.getVipId(), cmd.getVipServerUrl());
            return new Answer(cmd, true, url);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(AcquireNewIpForLbCommand cmd) {
        try {
            long globoNetworkLBEnvironmentId = cmd.getGloboNetworkLBEnvironmentId();
            Ip globoIp = _globoNetworkApi.getIpAPI().getAvailableIpForVip(globoNetworkLBEnvironmentId, "", cmd.isv6());
            if (globoIp == null) {
                return new Answer(cmd, false, "No available ip address for load balancer environment network " + globoNetworkLBEnvironmentId);
            }

            // get network information
            Long networkId = globoIp.getNetworkId();
            Network network = _globoNetworkApi.getNetworkAPI().getNetwork(networkId, cmd.isv6());
            if (network == null) {
                return new Answer(cmd, false, "Network with id " + networkId + " not found");
            }

            GloboNetworkAndIPResponse answer = (GloboNetworkAndIPResponse)this.createNetworkResponse(network, cmd);

            // ip information
            answer.setIp(globoIp.getIpString());
            answer.setIpId(globoIp.getId());

            return answer;
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(AddOrRemoveVipInGloboNetworkCommand cmd) {
        try {
            // FIXME Change default values to be class attributes rather than method variables
            Integer DEFAULT_REALS_PRIORITY = 0;
            Integer DEFAULT_MAX_CONN = 0;
            String DEFAULT_HEALTHCHECK_TYPE = "TCP";
            String HEALTHCHECK_HTTP_STRING = "HTTP";
            Long DEFAULT_ID_EXPECT_FOR_HTTP_HEALTHCHECK = 25L;
            Integer DEFAULT_TIMEOUT = 5;
            String DEFAULT_CACHE = "(nenhum)";

            // FIXME! These parameters will always be null?
            String l7Filter = null;
            List<Integer> realsWeights = null;

            Vip vip = null;
            if (cmd.getVipId() != null) {
                vip = _globoNetworkApi.getVipAPI().getById(cmd.getVipId());
            }

            if (cmd.getRuleState() == FirewallRule.State.Revoke) {
                if (vip == null) {
                    s_logger.warn("VIP already removed from GloboNetwork");
                    return new Answer(cmd, true, "VIP " + cmd.getIpv4() + " already removed from GloboNetwork");
                } else {
                    // Delete VIP
                    return this.removeVipFromGloboNetwork(cmd, vip.getId(), true);
                }
            }

            LbAlgorithm lbAlgorithm;
            if ("roundrobin".equals(cmd.getMethodBal())) {
                lbAlgorithm = LbAlgorithm.RoundRobin;
            } else if ("leastconn".equals(cmd.getMethodBal())) {
                lbAlgorithm = LbAlgorithm.LeastConn;
            } else {
                return new Answer(cmd, false, "Invalid balancing method provided.");
            }

            String lbPersistence;
            if (cmd.getPersistencePolicy() == null || "None".equals(cmd.getPersistencePolicy().getMethodName())) {
                lbPersistence = "(nenhum)";
            } else if ("Cookie".equals(cmd.getPersistencePolicy().getMethodName())) {
                lbPersistence = "cookie";
            } else if ("Source-ip".equals(cmd.getPersistencePolicy().getMethodName())) {
                lbPersistence = "source-ip";
            } else if ("Source-ip with persistence between ports".equals(cmd.getPersistencePolicy().getMethodName())) {
                lbPersistence = "source-ip com persist. entre portas";
            } else {
                return new Answer(cmd, false, "Invalid persistence policy provided.");
            }

            String healthcheckType;
            String healthcheck;
            Long expectedHealthcheckId;
            if (cmd.getHealthcheckPolicy() == null || cmd.getHealthcheckPolicy().isRevoked()) {
                healthcheckType = DEFAULT_HEALTHCHECK_TYPE;
                healthcheck = this.buildHealthcheckString(null, null);
                expectedHealthcheckId = null;
            } else {
                healthcheckType = HEALTHCHECK_HTTP_STRING;
                healthcheck = this.buildHealthcheckString(cmd.getHealthcheckPolicy().getpingpath(), cmd.getHost());
                expectedHealthcheckId = DEFAULT_ID_EXPECT_FOR_HTTP_HEALTHCHECK;
            }

            // Values that come directly from command
            String businessArea = cmd.getBusinessArea();
            String host = cmd.getHost();
            String serviceName = cmd.getServiceName();
            List<String> ports = cmd.getPorts();

            // Process IPs and set RealIP objects to create VIP
            List<RealIP> realsIp = new ArrayList<RealIP>();
            List<Integer> realsPriorities = new ArrayList<Integer>();
            for (GloboNetworkVipResponse.Real real : cmd.getRealList()) {
                Ip ip = _globoNetworkApi.getIpAPI().findByIpAndEnvironment(real.getIp(), real.getEnvironmentId(), false);
                if (ip == null) {
                    if (real.isRevoked()) {
                        s_logger.warn("There's an inconsistency. Real with IP " + real.getIp() + " was not found in GloboNetwork.");
                        continue;
                    } else {
                        return new Answer(cmd, false, "Could not get real IP information: " + real.getIp());
                    }
                }
                if (real.getPorts() == null) {
                    return new Answer(cmd, false, "You need to specify a port for the real");
                }

                // GloboNetwork considers a different RealIP object if there are multiple ports
                // even though IP and name info are the same
                for(String port : real.getPorts()) {
                    RealIP realIP = new RealIP();
                    realIP.setName(real.getVmName());
                    realIP.setRealIp(real.getIp());
                    realIP.setVipPort(Integer.valueOf(port.split(":")[0]));
                    realIP.setRealPort(Integer.valueOf(port.split(":")[1]));
                    realIP.setIpId(ip.getId());
                    realsIp.add(realIP);

                    // Making sure there is the same number of reals and reals priorities
                    realsPriorities.add(DEFAULT_REALS_PRIORITY);
                }
            }

            // Check VIP IP in its environment
            // Search for a VIP environment to get its info (needed for finality, client and environment)
            VipEnvironment environmentVip = _globoNetworkApi.getVipEnvironmentAPI().search(cmd.getVipEnvironmentId(), null, null, null);
            if (environmentVip == null) {
                return new Answer(cmd, false, "Could not find VIP environment " + cmd.getVipEnvironmentId());
            }
            String finality = environmentVip.getFinality();
            String client = environmentVip.getClient();
            String environment = environmentVip.getEnvironmentName();

            Ip ip = _globoNetworkApi.getIpAPI().checkVipIp(cmd.getIpv4(), cmd.getVipEnvironmentId(), false);
            if (ip == null) {
                return new Answer(cmd, false, "IP " + cmd.getIpv4() + " doesn't exist in VIP environment " + cmd.getVipEnvironmentId());
            }
            if (vip == null) {
                // Vip doesn't exist yet
                // Actually add the VIP to GloboNetwork
                vip = _globoNetworkApi.getVipAPI().add(ip.getId(), null, expectedHealthcheckId, finality, client, environment, DEFAULT_CACHE,
                        lbAlgorithm.getGloboNetworkBalMethod(), lbPersistence, healthcheckType, healthcheck, DEFAULT_TIMEOUT, host, DEFAULT_MAX_CONN, businessArea, serviceName,
                        l7Filter, realsIp, realsPriorities, realsWeights, ports, null);

                // Validate the vip
                _globoNetworkApi.getVipAPI().validate(vip.getId());

                // Create the vip on the equipment
                if (!vip.getCreated()) {
                    s_logger.info("Requesting GloboNetwork to create vip " + vip.getId());
                    _globoNetworkApi.getVipAPI().create(vip.getId());
                }
            } else if (vip.getCreated()) {
                // Is VIP already created on the equipment?
                // Update reals
                for (GloboNetworkVipResponse.Real real : cmd.getRealList()) {
                    if (real.isRevoked()) {
                        this.removeReal(vip, real.getVmName(), real.getIp(), real.getPorts());
                    } else {
                        this.addAndEnableReal(vip, real.getVmName(), real.getIp(), real.getPorts());
                    }
                }

                // Decide if we need to update persistence
                if (!lbPersistence.equals(vip.getPersistence())) {
                    _globoNetworkApi.getVipAPI().alterPersistence(vip.getId(), lbPersistence);
                }

                // Decide if we need to update healthcheck
                if ((vip.getHealthcheckType() == null && healthcheckType != null) || (vip.getHealthcheckType() != null && !vip.getHealthcheckType().equals(healthcheckType))
                        || (vip.getHealthcheck() == null && healthcheck != null) || (vip.getHealthcheck() != null && !vip.getHealthcheck().equals(healthcheck))
                        || (vip.getExpectedHealthcheckId() == null && expectedHealthcheckId != null)
                        || (vip.getExpectedHealthcheckId() != null && vip.getExpectedHealthcheckId() == expectedHealthcheckId)) {
                    _globoNetworkApi.getVipAPI().alterHealthcheck(vip.getId(), healthcheckType, healthcheck, expectedHealthcheckId);
                }
            } else {
                // Otherwise, we update the VIP and validate it
                _globoNetworkApi.getVipAPI().alter(vip.getId(), ip.getId(), null, expectedHealthcheckId, vip.getValidated(), vip.getCreated(), finality, client, environment,
                        DEFAULT_CACHE, lbAlgorithm.getGloboNetworkBalMethod(), lbPersistence, healthcheckType, healthcheck, DEFAULT_TIMEOUT, host, DEFAULT_MAX_CONN, businessArea,
                        serviceName, l7Filter, realsIp, realsPriorities, realsWeights, ports, null);

                _globoNetworkApi.getVipAPI().validate(vip.getId());
            }
            vip = _globoNetworkApi.getVipAPI().getById(vip.getId());
            vip.setIpv4Id(ip.getId());

            return this.createVipResponse(vip, cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    protected String buildHealthcheckString(String path, String host) {
        if (path == null || host == null) {
            return "";
        }
        // when path is complete http request, keep it.
        if (path.startsWith("GET") || path.startsWith("POST")) {
            return path;
        }
        return "GET " + path + " HTTP/1.0\\r\\nHost: " + host + "\\r\\n\\r\\n";
    }

    protected boolean addAndEnableReal(Vip vip, String equipName, String realIpAddr, List<String> realPorts) throws GloboNetworkException {
        Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(equipName);
        if (equipment == null) {
            // Equipment doesn't exist
            return false;
        }

        List<Ip> ips = _globoNetworkApi.getIpAPI().findIpsByEquipment(equipment.getId());
        Ip ip = null;
        for (Ip equipIp : ips) {
            String equipIpString = equipIp.getIpString();
            if (equipIpString.equals(realIpAddr)) {
                ip = equipIp;
            }
        }

        if (ip == null) {
            return false;
        }

        if (vip.getRealsIp() != null) {
            for (RealIP realIp : vip.getRealsIp()) {
                if (ip.getId().equals(realIp.getIpId())) {
                    // real already added. Only ensure is enabled
                    s_logger.info("Enabling real " + ip.getIpString() + " on loadbalancer " + vip.getId());
                    _globoNetworkApi.getVipAPI().enableReal(vip.getId(), ip.getId(), equipment.getId(), realIp.getVipPort(), realIp.getRealPort());
                    return true;
                }
            }
        }

        // added reals are always enabled by default
        s_logger.info("Adding real " + ip.getIpString() + " on loadbalancer " + vip.getId());
        for(String realPort : realPorts) {
            _globoNetworkApi.getVipAPI().addReal(vip.getId(), ip.getId(), equipment.getId(), Integer.valueOf(realPort.split(":")[0]), Integer.valueOf(realPort.split(":")[1]));
        }
        return true;
    }

    protected boolean removeReal(Vip vip, String equipName, String realIpAddr, List<String> realPorts) throws GloboNetworkException {
        Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(equipName);
        if (equipment == null) {
            // Equipment doesn't exist. So, there is no Vip either.
            return false;
        }

        if (vip.getRealsIp() != null) {
            for (RealIP realIp : vip.getRealsIp()) {
                if (realIpAddr.equals(realIp.getRealIp())) {
                    // real exists in vip. Remove it.
                    s_logger.info("Removing real " + realIpAddr + " from loadbalancer " + vip.getId());
                    for (String realPort : realPorts) {
                        _globoNetworkApi.getVipAPI().removeReal(vip.getId(), realIp.getIpId(), equipment.getId(), Integer.valueOf(realPort.split(":")[0]), Integer.valueOf(realPort.split(":")[1]));
                    }
                    return true;
                }
            }
        }
        s_logger.info("Can't remove real " + realIpAddr + " from loadbalancer " + vip.getId() + "  because isn't in LB");
        return true;
    }

    protected Answer createVipResponse(Vip vip, Command cmd) {
        if (vip == null || vip.getId() == null) {
            return new Answer(cmd, false, "Vip request was not created in GloboNetwork");
        }

        try {
            // Using a map rather than a list because different ports come in different objects
            // even though they have the same ID
            // Example
            // {
            //    "id_ip": "33713",
            //    "port_real": "8180",
            //    "port_vip": "80",
            //    "real_ip": "10.20.30.40",
            //    "real_name": "MACHINE01"
            // },
            // {
            //    "id_ip": "33713",
            //    "port_real": "8280",
            //    "port_vip": "80",
            //    "real_ip": "10.20.30.40",
            //    "real_name": "MACHINE01"
            // },

            Map<Long, Real> reals = new HashMap<Long, Real>();
            for (RealIP real : vip.getRealsIp()) {
                Real realResponse = reals.get(real.getIpId());
                if (realResponse == null) {
                    // Doesn't exist yet, first time iterating, so add IP parameter and add to list
                    realResponse = new Real();
                    realResponse.setIp(real.getRealIp());
                    realResponse.setVmName(real.getName());
                    reals.put(real.getIpId(), realResponse);
                }
                realResponse.getPorts().addAll(vip.getServicePorts());
            }

            // Fill vip object with ip id and vip environment id
            VipEnvironment vipEnvironment = _globoNetworkApi.getVipEnvironmentAPI().search(null, vip.getFinality(), vip.getClient(), vip.getEnvironment());
            if (vipEnvironment == null) {
                throw new GloboNetworkException("Vip Environment not found for vip " + vip.getId());
            }

            Ip ip = _globoNetworkApi.getIpAPI().checkVipIp(vip.getIps().get(0), vipEnvironment.getId(), false);
            if (ip == null) {
                throw new GloboNetworkException("Vip IP not found for vip " + vip.getId());
            }

            GloboNetworkVipResponse vipResponse = new GloboNetworkVipResponse(cmd, vip.getId(), // id
                    vip.getHost(), // name
                    vip.getIps().size() == 1 ? vip.getIps().get(0) : vip.getIps().toString(), // ip
                    ip.getId(), //ip id
                    vipEnvironment.getId(), null, // network
                    vip.getCache(), // cache
                    vip.getMethod(), // method
                    vip.getPersistence(), // persistence
                    vip.getHealthcheckType(), // healtcheck type
                    vip.getHealthcheck(), // healthcheck
                    vip.getMaxConn(), // maxconn,
                    vip.getServicePorts(), reals.values(), vip.getCreated());

            return vipResponse;
        } catch (GloboNetworkException e) {
            return new Answer(cmd, e);
        }

    }

    private Answer createResponse(Vlan vlan, Command cmd) {

        if (vlan.getIpv4Networks().isEmpty() && vlan.getIpv6Networks().isEmpty()) {
            // Error code 116 from GloboNetwork: 116 : VlanNaoExisteError,
            return new GloboNetworkErrorAnswer(cmd, 116, "No networks in this VLAN");
        }

        String vlanName = vlan.getName();
        String vlanDescription = vlan.getDescription();
        Long vlanId = vlan.getId();
        Long vlanNum = vlan.getVlanNum();
        Network network = vlan.getNetworks().get(0);

        return new GloboNetworkVlanResponse(cmd, vlanId, vlanName, vlanDescription, vlanNum, network.getNetworkAddressAsString(),
                network.getMaskAsString(), network.getId(), network.getActive(), network.getBlock(), network.isv6());
    }

    private Answer createNetworkResponse(Network network, Command cmd) throws GloboNetworkException {
        GloboNetworkAndIPResponse answer = new GloboNetworkAndIPResponse(cmd);
        answer.setNetworkId(network.getId());
        answer.setVipEnvironmentId(network.getVipEnvironmentId());
        answer.setNetworkAddress(network.getNetworkAddressAsString());
        answer.setNetworkMask(network.getMaskAsString());
        answer.setActive(Boolean.TRUE.equals(network.getActive()));
        answer.setNetworkCidr(network.getNetworkAddressAsString() + "/" + network.getBlock());
        answer.setIsv6(network.isv6());

        // get vlan information
        Long vlanId = network.getVlanId();
        Vlan vlan = _globoNetworkApi.getVlanAPI().getById(vlanId);
        if (vlan == null) {
            return new Answer(cmd, false, "Vlan with id " + vlanId + " not found");
        }
        answer.setVlanId(vlanId);
        answer.setVlanName(vlan.getName());
        answer.setVlanDescription(vlan.getDescription());
        answer.setVlanNum(vlan.getVlanNum().intValue());
        return answer;
    }
}
