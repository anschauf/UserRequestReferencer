package preclassification;

import com.mongodb.*;
import helper.Constants;
import org.bson.types.ObjectId;
import subclassification.subclasses.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;

/**
 * if 'testmode' = true, update reviews from 'preclassification_test.csv' into DB 'testReviews' with manually written preclassification
 * if 'testmode' = false, update reviews from 'preclassification.csv' into DB 'reviews'
 */
public class PreclassificationDBImport implements Constants {

    DB db;
    DBCollection collection;

    public PreclassificationDBImport(String dbName) {
        int portnumb = MONGODB_PORT;
        String collectionName = REVIEW_COLLLECTION;

        MongoClient mongoClient = new MongoClient("Localhost", portnumb);
        db = mongoClient.getDB(dbName);
        this.collection = db.getCollection(collectionName);
    }

    public void importPreClassificationIntoDB() {
        try {
            BufferedReader br = new BufferedReader(new FileReader(PRECLASSIFICATION_IMPORT_PATH));
            String line = br.readLine();
            int lineCount = 0;
            while (line != null) {
                lineCount++;
                try {
                    //use separator
                    String[] review = line.split(CSV_SPLIT);
                    addPreClassificationInDB(review, lineCount);

                } catch (Exception e) {
                    System.err.println("CSVReader: could not read in line #"
                            + lineCount + "\t" + e);
                }
                line = br.readLine();
            }
            System.out.println("Successfully imported the preclassification-values into the DB");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addPreClassificationInDB(String[] review, int lineNumber) {
        String id = review[0];
        PreClassification preClassification = null;
        String subClassification = null;

        if (review.length > 2) {
            preClassification = PreClassification.valueOf(review[2]);

            BasicDBObject filter = new BasicDBObject().append("_id", new ObjectId(id));
            BasicDBObject newPreClassification = new BasicDBObject();
            newPreClassification.put("preclassification", preClassification.toString());
            BasicDBObject updateOperationDocument = new BasicDBObject("$set", newPreClassification);
            collection.update(filter, updateOperationDocument);
        }

        if (preClassification != null && review.length > 3) {
            subClassification = checkCorrectSubClass(preClassification, review[3]);

            if (subClassification != null) {
                BasicDBObject filter = new BasicDBObject().append("_id", new ObjectId(id));
                BasicDBObject newSubClassification = new BasicDBObject();
                newSubClassification.put("subclassification", subClassification.toString());
                BasicDBObject updateOperationDocument = new BasicDBObject("$set", newSubClassification);
                collection.update(filter, updateOperationDocument);
            }
        }
    }

    //checks if the subclass is correct and from the preclassification. returns 'null' if not so
    private String checkCorrectSubClass(PreClassification preClassification, String subClassText) {
        switch (preClassification) {
            case USAGE: {
                try {
                    return SUBCLASS_USAGE.valueOf(subClassText).toString();
                } catch (Exception e) {
                    return null;
                }
            }
            case RESSOURCES: {
                try {
                    return SUBCLASS_RESSOURCES.valueOf(subClassText).toString();
                } catch (Exception e) {
                    return null;
                }
            }
            case PRICING: {
                try {
                    return SUBCLASS_PRICING.valueOf(subClassText).toString();
                } catch (Exception e) {
                    return null;
                }
            }
            case PROTECTION: {
                try {
                    return SUBCLASS_PROTECTION.valueOf(subClassText).toString();
                } catch (Exception e) {
                    return null;
                }
            }
            case COMPATIBILITY: {
                try {
                    return SUBCLASS_COMPATIBILITY.valueOf(subClassText).toString();
                } catch (Exception e) {
                    return null;
                }
            }
            default:
                return null;
        }
    }

    public boolean collectionExists(final String collectionName) {
        Set<String> collectionNames = db.getCollectionNames();
        for (final String name : collectionNames) {
            if (name.equalsIgnoreCase(collectionName)) {
                return true;
            }
        }
        return false;
    }
}
