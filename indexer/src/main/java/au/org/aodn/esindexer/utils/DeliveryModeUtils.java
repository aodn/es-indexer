package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;

import java.util.Optional;

public class DeliveryModeUtils {
    public enum DeliveryMode {
        other("other"),
        completed("completed"),
        real_time("real-time"),
        delayed("delayed");

        private final String mode;

        DeliveryMode(String mode) {
            this.mode = mode;
        }

        @Override
        public String toString() {
            return mode;
        }
    }

    protected static Optional<DeliveryMode> scanRealTimeOrDelayed(String text) {
        if (text != null) {
            text = text.toLowerCase();
            if(text.contains("real time") || text.contains("realtime") || text.contains("real-time")) {
                return Optional.of(DeliveryMode.real_time);
            }
            else if(text.contains("delayed")) {
                return Optional.of(DeliveryMode.delayed);
            }
        }
        return Optional.empty();
    }
    /**
     * https://whiteboard.office.com/me/whiteboards/p/c3BvOmh0dHBzOi8vdW5pdmVyc2l0eXRhc21hbmlhLW15LnNoYXJlcG9pbnQuY29tL3BlcnNvbmFsL3ZpY3RvcmlhX2lzYWFjX3V0YXNfZWR1X2F1/b!jyy_u0MXG0WfUik3ryg6CFXwBqodQeBElg2KLaztJGVQAZ-9v_9CSpHNf-HyaQSK/01M5FSETRMNJ57BNUYWBBKKFDNAGDJBM45
     *
     * Logic to guess if it is IMOS data or not from somewhere else.
     * 1. Status
     *  Other - OTHER
     *  Completed - COMPLETED
     *  Ongoing  -
     *      2. Title contains
     *          Real-time - REAL_TIME
     *          Delayed - DELAYED
     *          3. Check Abstract
     *              Contains Real-time - REAL_TIME
     *              Contains Delayed - DELAYED
     *              4. Check Lineage
     *                  Contains Real-time - REAL_TIME
     *                  Contains Delayed - DELAYED
     *                  Else - OTHER
     * @return - The delivery mode enum
     */
    public static DeliveryMode getDeliveryMode(final MDMetadataType source) {
        String status = SummariesUtils.getStatus(source);

        if(status != null) {
            String normalisedStatus = normaliseStatus(status);

            if(normalisedStatus.contains("completed")) {
                return DeliveryMode.completed;
            }
            else if(normalisedStatus.contains("ongoing")) {
                return scanRealTimeOrDelayed(CommonUtils.getTitle(source))
                        .orElse(
                                scanRealTimeOrDelayed(CommonUtils.getDescription(source))
                                    .orElse(
                                            scanRealTimeOrDelayed(SummariesUtils.getStatement(source))
                                                .orElse(DeliveryMode.other)
                                    )
                        );
            }
        }
        return DeliveryMode.other;
    }

    /**
     * The status field is provided by data provider, which can be not generic to 'completed' or 'onGoing'.
     * See examples: https://github.com/aodn/backlog/issues/7978#issuecomment-3809180417
     * This function normalizes non-standard status values from data providers to standardized terms 'completed' or 'ongoing' according to a mapping rule:
     * https://github.com/aodn/backlog/issues/7978#issuecomment-3821113084
     * so that to consistent delivery mode determination.
     * */
    public static String normaliseStatus (String customisedStatus) {
        if (customisedStatus == null) {
            return null;
        }

        String lowerStatus = customisedStatus.toLowerCase();
        if (lowerStatus.equals("historicalarchive") ||
                lowerStatus.equals("obsolete") ||
                lowerStatus.equals("deprecated") ||
                lowerStatus.contains("complete")
        ) {
            return "completed";
        }
        // some record has a status of 'on going' so check with a regex mapping
        else if (lowerStatus.matches(".*on\\s?going.*")) {
            return "ongoing";
        }
        return customisedStatus;
    }
}
