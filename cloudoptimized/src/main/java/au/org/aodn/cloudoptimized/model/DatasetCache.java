package au.org.aodn.cloudoptimized.model;

import au.org.aodn.cloudoptimized.model.geojson.FeatureCollectionGeoJson;
import au.org.aodn.stac.model.StacItemModel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.util.List;
import java.util.Map;

@Slf4j
@Getter
public class DatasetCache {
    protected FeatureCollectionGeoJson featureCollection;


    public void addData(FeatureCollectionGeoJson featureCollection) {
        if (this.featureCollection == null) {
            this.featureCollection = featureCollection;
            return;
        }
        StopWatch timer = new StopWatch();
        timer.start("Data adding");
        this.featureCollection.getFeatures().addAll(featureCollection.getFeatures());
        timer.stop();
        if (timer.getTotalTimeSeconds() > 1) {
            log.info(timer.prettyPrint());
        }
    }

    @SuppressWarnings("unchecked")
    private void addNewDateToModel(StacItemModel sameLocationModel, DateCountPair pair) {
        var dates = (List<DateCountPair>) sameLocationModel.getProperties().get("dates");
        dates.add(pair);
    }

    @SuppressWarnings("unchecked")
    private DateCountPair getDateCountPair(StacItemModel itemToAdd) {
        var dates = (List<DateCountPair>) itemToAdd.getProperties().get("dates");
        if (dates.size() != 1) {
            // assume this function is only for initial model (no multiple dates contained)
            throw new RuntimeException("Invalid dates");
        }
        return dates.get(0);
    }


//    private StacItemModel findSameLocationModel(StacItemModel model) {
//        var coordToCompare = getItemCoordinate(model);
//        for (var item : data) {
//            var existingCoord = getItemCoordinate(item);
//            if (existingCoord.equals(coordToCompare)) {
//                return item;
//            }
//        }
//        return null;
//    }

    @SuppressWarnings("unchecked, rawtypes")
    private Coordinate getItemCoordinate(StacItemModel item) {
        var geometry = (Map<String, List>) item.getGeometry();
        var geometries = geometry.get("geometries");
        if (geometries.size() != 1) {
            throw new RuntimeException("Invalid geometry");
        }
        var point = (Map) geometries.get(0);
        var coordinates = (List) point.get("coordinates");
        Double lat = null;
        Double lon = null;
        if (coordinates.get(1) instanceof  Integer) {
            lat = ((Integer) coordinates.get(1)).doubleValue();
        } else if (coordinates.get(1) instanceof Double) {
            lat = (Double) coordinates.get(1);
        }
        if (coordinates.get(0) instanceof  Integer) {
            lon = ((Integer) coordinates.get(0)).doubleValue();
        } else if (coordinates.get(0) instanceof Double) {
            lon = (Double) coordinates.get(0);
        }
        if (lat == null || lon == null) {
            throw new RuntimeException("Invalid coordinates");
        }
        return new Coordinate(lat, lon);
    }
}
