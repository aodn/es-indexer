package au.org.aodn.esindexer.utils;

import java.util.Arrays;
import java.util.List;

import au.org.aodn.stac.model.RelationType;
import au.org.aodn.stac.util.JsonUtil;

/**
 * Protocols here are referenced from old portal project(Grails). Currently, didn't
 * implement all of them. Others can be added if needed.
 */
public class LinkUtils {
    private record TitleWithDescription(String title, String description) {}

    // protocols as reference
    private final static List<String> WMS = Arrays.asList(
            "OGC:WMS-1.1.1-http-get-map",
            "OGC:WMS-1.3.0-http-get-map",
            "OGC:WMS",
            "IMOS:NCWMS--proto"
    ) ;

    private final static List<String> WFS = Arrays.asList(
            "OGC:WFS-1.0.0-http-get-capabilities",
            "OGC:WFS-1.0.0-http-get-feature--shapefile",
            "OGC:WFS"
    );

    private final static List<String> DATA = Arrays.asList(
            "WWW:DOWNLOAD-1.0-http--download",
            "WWW:DOWNLOAD-1.0-http--downloadother",
            "WWW:DOWNLOAD-1.0-http--downloaddata",
            "WWW:LINK-1.0-http--downloaddata",
            "WWW:DOWNLOAD-1.0-ftp--download"
    );

    private final static List<String> RELATED= Arrays.asList(
            "WWW:LINK-1.0-http--link",
            "WWW:LINK-1.0-http--related"
    );

    private final static List<String> METADATA = List.of(
            "WWW:LINK-1.0-http--metadata-URL"
    );

    /**
     * Maps protocol strings to their corresponding RelationType based on protocol groups.
     *
     * @param protocol The protocol string to map
     * @return The corresponding RelationType value, or protocol string as default
     */
    public static String getRelationType(String protocol) {
        if (WMS.contains(protocol) || protocol.startsWith("OGC:WMS") || protocol.startsWith("IMOS:NCWMS")) {
            return RelationType.WMS.getValue();
        }

        if (WFS.contains(protocol) || protocol.startsWith("OGC:WFS")) {
            return RelationType.WFS.getValue();
        }

        if (DATA.contains(protocol) || protocol.startsWith("WWW:DOWNLOAD")) {
            return RelationType.DATA.getValue();
        }

        if (RELATED.contains(protocol)) {
            return RelationType.RELATED.getValue();
        }

        if (METADATA.contains(protocol)) {
            return RelationType.METADATA.getValue();
        }

        // Default case - return protocol itself
        return protocol;
    }

    // build link title with title and description
    public static String buildTitleJsonString(String title, String description) {
        var titleWithDescription = new TitleWithDescription(title, description);
        return JsonUtil.toJsonString(titleWithDescription);
    }
}
