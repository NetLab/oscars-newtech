<#-- @ftlvariable name="vpls" type="net.es.oscars.dto.pss.params.alu.AluVpls" -->
<#-- @ftlvariable name="sap" type="net.es.oscars.dto.pss.params.alu.AluSap" -->
<#-- @ftlvariable name="sdp" type="net.es.oscars.dto.pss.params.alu.AluSdp" -->

<#assign svcId = vpls.svcId>
/configure service vpls ${svcId} shutdown


<#if vpls.sdpToVcIds??>
<#list vpls.sdpToVcIds as sdpToVcId>
<#assign sdpId = sdpToVcId.sdpId>
<#assign vcId = sdpToVcId.vcId>
/configure service vpls ${svcId} spoke-sdp ${sdpId}:${vcId} shutdown
/configure service vpls ${svcId} no spoke-sdp ${sdpId}:${vcId}
</#list>
</#if>


<#list vpls.saps as sap>
<#assign sapId = sap.port+":"+sap.vlan>
/configure service vpls ${svcId} sap ${sapId} shutdown
/configure service vpls ${svcId} no sap ${sapId}
</#list>

/configure service no vpls ${svcId}



