package de.jplag.clustering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.jplag.JPlagComparison;
import de.jplag.Submission;
import de.jplag.clustering.algorithm.GenericClusteringAlgorithm;

/**
 * Runs the clustering according to an options object.
 */
public class ClusteringFactory {
    private static final int LOGGED_MEMBERS = 5;
    private static final String CLUSTER_PATTERN = "avg similarity: {}, strength: {}, {} members: {}";
    private static final String CLUSTERING_RESULT = "{} clusters were found:";
    private static final String CLUSTERING_PARAMETERS = "Calculating clusters via {} clustering with {} pre-processing...";
    private static final String CLUSTERING_DISABLED = "Cluster calculation disabled (as requested)!";
    private static final Logger logger = LoggerFactory.getLogger(ClusteringFactory.class);

    public static List<ClusteringResult<Submission>> getClusterings(Collection<JPlagComparison> comparisons, ClusteringOptions options) {
        if (!options.enabled()) {
            logger.warn(CLUSTERING_DISABLED);
            return Collections.emptyList();
        } else {
            logger.info(CLUSTERING_PARAMETERS, options.algorithm(), options.preprocessor());
        }

        // init algorithm
        GenericClusteringAlgorithm clusteringAlgorithm = options.algorithm().create(options);

        // init preprocessor
        Optional<ClusteringPreprocessor> preprocessor = options.preprocessor().constructPreprocessor(options);

        if (preprocessor.isPresent()) {
            // Package preprocessor into a clustering algorithm
            clusteringAlgorithm = new PreprocessedClusteringAlgorithm(clusteringAlgorithm, preprocessor.orElseThrow());
        }

        // init adapter
        ClusteringAdapter adapter = new ClusteringAdapter(comparisons, options.similarityMetric());

        // run clustering
        ClusteringResult<Submission> result = adapter.doClustering(clusteringAlgorithm);

        // remove bad clusters
        result = removeBadClusters(result);
        logClusters(result);

        return List.of(result);
    }

    private static ClusteringResult<Submission> removeBadClusters(final ClusteringResult<Submission> clustering) {
        List<Cluster<Submission>> filtered = clustering.getClusters().stream().filter(cluster -> !cluster.isBadCluster()).toList();
        return new ClusteringResult<>(filtered, clustering.getCommunityStrength());
    }

    private static void logClusters(ClusteringResult<Submission> result) {
        var clusters = new ArrayList<>(result.getClusters());
        clusters.sort((first, second) -> Double.compare(second.getAverageSimilarity(), first.getAverageSimilarity()));
        logger.info(CLUSTERING_RESULT, clusters.size());
        clusters.forEach(ClusteringFactory::logCluster);
    }

    private static void logCluster(Cluster<Submission> cluster) {
        String members = membersToString(cluster.getMembers());
        logger.info(CLUSTER_PATTERN, cluster.getAverageSimilarity(), cluster.getCommunityStrength(), cluster.getMembers().size(), members);
    }

    private static String membersToString(Collection<Submission> members) {
        if (members.size() > LOGGED_MEMBERS) {
            return List.copyOf(members).subList(0, LOGGED_MEMBERS).toString().replace("]", "...");
        }
        return members.toString();
    }
}
