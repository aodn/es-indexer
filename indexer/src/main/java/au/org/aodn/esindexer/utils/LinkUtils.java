package au.org.aodn.esindexer.utils;

import java.util.Arrays;
import java.util.List;

/**
 * Protocols here are referenced from old portal project(Grails). Currently, didn't
 * implement all of them. Others can be added if needed.
 */
@SuppressWarnings("unused")
public class LinkUtils {

    // protocols as reference
    private final static List<String> WMS = Arrays.asList(
            "OGC:WMS-1.1.1-http-get-map",
            "OGC:WMS-1.3.0-http-get-map",
            "OGC:WMS",
            "IMOS:NCWMS--proto"
    ) ;

    private final static List<String> WFS = Arrays.asList(
            "OGC:WFS-1.0.0-http-get-capabilities",
            "OGC:WFS"
    );

    private final static List<String> DATA_FILE = Arrays.asList(
            "WWW:DOWNLOAD-1.0-http--download",
            "WWW:DOWNLOAD-1.0-http--downloadother",
            "WWW:DOWNLOAD-1.0-http--downloaddata",
            "WWW:LINK-1.0-http--downloaddata"
    );

    private final static List<String> SUPPLEMENTARY= Arrays.asList(
            "WWW:LINK-1.0-http--link",
            "WWW:LINK-1.0-http--downloaddata",
            "WWW:LINK-1.0-http--related",
            "WWW:DOWNLOAD-1.0-ftp--download"
    );

    private final static List<String> METADATA_RECORD = List.of(
            "WWW:LINK-1.0-http--metadata-URL"
    );

    public static boolean isWmsOrWfs (String protocol) {
        return protocol.contains("OGC:WMS")
                || protocol.contains("OGC:WFS")
                || protocol.contains("IMOS:NCWMS")
                ;
    }

}
