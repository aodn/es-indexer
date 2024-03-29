<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:gmd="http://www.isotc211.org/2005/gmd"
                xmlns:gcoold="http://www.isotc211.org/2005/gco"
                xmlns:gmi="http://www.isotc211.org/2005/gmi"
                xmlns:gmx="http://www.isotc211.org/2005/gmx"
                xmlns:gsr="http://www.isotc211.org/2005/gsr"
                xmlns:gss="http://www.isotc211.org/2005/gss"
                xmlns:gts="http://www.isotc211.org/2005/gts"
                xmlns:srvold="http://www.isotc211.org/2005/srv"
                xmlns:gml30="http://www.opengis.net/gml"
                xmlns:cat="http://standards.iso.org/iso/19115/-3/cat/1.0"
                xmlns:cit="http://standards.iso.org/iso/19115/-3/cit/1.0"
                xmlns:gcx="http://standards.iso.org/iso/19115/-3/gcx/1.0"
                xmlns:gex="http://standards.iso.org/iso/19115/-3/gex/1.0"
                xmlns:lan="http://standards.iso.org/iso/19115/-3/lan/1.0"
                xmlns:srv="http://standards.iso.org/iso/19115/-3/srv/2.0"
                xmlns:mac="http://standards.iso.org/iso/19115/-3/mac/1.0"
                xmlns:mas="http://standards.iso.org/iso/19115/-3/mas/1.0"
                xmlns:mcc="http://standards.iso.org/iso/19115/-3/mcc/1.0"
                xmlns:mco="http://standards.iso.org/iso/19115/-3/mco/1.0"
                xmlns:mda="http://standards.iso.org/iso/19115/-3/mda/1.0"
                xmlns:mdb="http://standards.iso.org/iso/19115/-3/mdb/1.0"
                xmlns:mdt="http://standards.iso.org/iso/19115/-3/mdt/1.0"
                xmlns:mex="http://standards.iso.org/iso/19115/-3/mex/1.0"
                xmlns:mic="http://standards.iso.org/iso/19115/-3/mic/1.0"
                xmlns:mil="http://standards.iso.org/iso/19115/-3/mil/1.0"
                xmlns:mrl="http://standards.iso.org/iso/19115/-3/mrl/1.0"
                xmlns:mds="http://standards.iso.org/iso/19115/-3/mds/1.0"
                xmlns:mmi="http://standards.iso.org/iso/19115/-3/mmi/1.0"
                xmlns:mpc="http://standards.iso.org/iso/19115/-3/mpc/1.0"
                xmlns:mrc="http://standards.iso.org/iso/19115/-3/mrc/1.0"
                xmlns:mrd="http://standards.iso.org/iso/19115/-3/mrd/1.0"
                xmlns:mri="http://standards.iso.org/iso/19115/-3/mri/1.0"
                xmlns:mrs="http://standards.iso.org/iso/19115/-3/mrs/1.0"
                xmlns:msr="http://standards.iso.org/iso/19115/-3/msr/1.0"
                xmlns:mai="http://standards.iso.org/iso/19115/-3/mai/1.0"
                xmlns:mdq="http://standards.iso.org/iso/19157/-2/mdq/1.0"
                xmlns:gco="http://standards.iso.org/iso/19115/-3/gco/1.0"
                xmlns:gml="http://www.opengis.net/gml/3.2"
                xmlns:xlink="http://www.w3.org/1999/xlink"
                xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl"
                exclude-result-prefixes="#all">

  <xsl:import href="utility/create19115-3Namespaces.xsl"/>
  <xsl:import href="utility/dateTime.xsl"/>
  <xsl:import href="utility/multiLingualCharacterStrings.xsl"/>

  <xd:doc xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl" scope="stylesheet">
    <xd:desc>
      <xd:p>
        <xd:b>Created on:</xd:b>March 8, 2014 </xd:p>
      <xd:p>Translates from ISO 19139 for ISO 19115 and ISO 19139-2 for 19115-2 to ISO 19139-1 for ISO 19115-1</xd:p>
      <xd:p>
        <xd:b>Version June 13, 2014</xd:b>
        <xd:ul>
          <xd:li>Converged the 19115-2 transform into 19115-1 namespaces</xd:li>
        </xd:ul>
      </xd:p>
      <xd:p>
        <xd:b>Version August 7, 2014</xd:b>
        <xd:ul>
          <xd:li>Changed namespace dates to 2014-07-11</xd:li>
          <xd:li>Fixed DistributedComputingPlatform element</xd:li>
        </xd:ul>
      </xd:p>
      <xd:p>
        <xd:b>Version August 15, 2014</xd:b>
        <xd:ul>
          <xd:li>Add multilingual metadata support by converting gmd:locale and copying gmd:PT_FreeText and element attributes (eg. gco:nilReason, xsi:type) for gmd:CharacterString elements (Author:
            fx.prunayre@gmail.com).</xd:li>
        </xd:ul>
      </xd:p>
      <xd:p>
        <xd:b>Version September 4, 2014</xd:b>
        <xd:ul>
          <xd:li>Added transform for MD_FeatureCatalogueDescription (problem identified by Tobias Spears</xd:li>
        </xd:ul>
      </xd:p>
      <xd:p>
        <xd:b>Version February 5, 2015</xd:b>
        <xd:ul>
          <xd:li>Update to 2014-12-25 version</xd:li>
        </xd:ul>
      </xd:p>
      <xd:p><xd:b>Author:</xd:b>thabermann@hdfgroup.org</xd:p>
    </xd:desc>
  </xd:doc>

  <xsl:output method="xml" indent="yes"/>

  <xsl:strip-space elements="*"/>

  <xsl:variable name="stylesheetVersion" select="'0.1'"/>


  <xsl:template match="/">
    <!--
    root element (MD_Metadata or MI_Metadata)
    -->
    <xsl:for-each select="/*">
      <xsl:variable name="nameSpacePrefix">
        <xsl:call-template name="getNamespacePrefix"/>
      </xsl:variable>

      <xsl:element name="mdb:MD_Metadata">
        <!-- new namespaces -->
        <xsl:call-template name="add-iso19115-3-namespaces"/>

        <xsl:apply-templates select="gmd:fileIdentifier" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:language" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:characterSet" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:parentIdentifier" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:hierarchyLevel" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:contact" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:dateStamp" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:metadataStandardName" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:locale" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:spatialRepresentationInfo" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:referenceSystemInfo" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:metadataExtensionInfo" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:identificationInfo" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:contentInfo" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:distributionInfo" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:dataQualityInfo" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:portrayalCatalogueInfo" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:metadataConstraints" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:applicationSchemaInfo" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmd:metadataMaintenance" mode="from19139to19115-3"/>
        <xsl:apply-templates select="gmi:acquisitionInformation" mode="from19139to19115-3"/>
      </xsl:element>
    </xsl:for-each>
  </xsl:template>


  <xsl:include href="mapping/core.xsl"/>
  <xsl:include href="mapping/CI_ResponsibleParty.xsl"/>
  <xsl:include href="mapping/CI_Citation.xsl"/>
  <xsl:include href="mapping/SRV.xsl"/>
  <xsl:include href="mapping/DQ.xsl"/>
</xsl:stylesheet>
