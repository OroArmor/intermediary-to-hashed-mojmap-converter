package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.jetbrains.annotations.Nullable;

public final class MavenFileDownloader {
    public static final List<String> MAVEN_REPOSITORIES = List.of(
            "https://maven.quiltmc.org/repository/release",
            "https://maven.quiltmc.org/repository/snapshot",
            "https://maven.quiltmc.org/repository/fabricmc"
    );

    public static void downloadFile(MavenArtifact mavenArtifact, Path output) throws IOException {
        for (String maven : MAVEN_REPOSITORIES) {
            URL url;
            if (mavenArtifact.isSnapshot()) {
                if (mavenArtifact.isUniqueSnapshot()) {
                    url = new URL(maven + "/" + mavenArtifact.getMavenPath() + "/" + mavenArtifact.getArtifactName() + ".jar");
                } else {
                    URL metadataURL = new URL(maven + "/" + mavenArtifact.getMavenMetadataPath());
                    JsonNode metadata;
                    try {
                        metadata = new XmlMapper().readTree(metadataURL).get("versioning").get("snapshot");
                    } catch (Exception e) {
                        continue;
                    }
                    String recentVersion = mavenArtifact.version().substring(0, mavenArtifact.version().length() - 9) + "-" + metadata.get("timestamp").asText() + "-" + metadata.get("buildNumber").asText();
                    url = new URL(maven + "/" + mavenArtifact.getMavenPath() + "/" + mavenArtifact.withVersion(recentVersion).getArtifactName() + ".jar");
                }
            } else {
                url = new URL(maven + "/" + mavenArtifact.getMavenArtifactPath(".jar"));
            }

            try (InputStream stream = url.openStream()) {
                if (!Files.exists(output)) {
                    Files.createFile(output);
                }
                Files.write(output, stream.readAllBytes());
            } catch (IOException e) {
                continue;
            }
            return;
        }

        throw new RuntimeException("Unable to find artifact " + mavenArtifact);
    }

    public record MavenArtifact(String group, String artifactId, String version, @Nullable String classifier, Matcher uniqueSnapshotVersionMatcher) {
        private static final Pattern UNIQUE_SNAPSHOT = Pattern.compile("(.+)-\\d{8}\\.\\d{6}-\\d+");

        public String getArtifactName() {
            return artifactId + "-" + version + (classifier == null ? "" : "-" + classifier);
        }

        public String getMavenMetadataPath() {
            return getMavenPath() + "/maven-metadata.xml";
        }

        public String getMavenPath() {
            return group.replace(".", "/") + "/" + artifactId + "/" + (!isUniqueSnapshot() ? version : getNonUniqueSnapshot());
        }

        public String getMavenArtifactPath(String fileExtension) {
            return getMavenPath() + "/" + getArtifactName() + fileExtension;
        }

        public MavenArtifact withVersion(String newVersion) {
            return new MavenArtifact(classifier, artifactId, newVersion, classifier, UNIQUE_SNAPSHOT.matcher(newVersion));
        }

        public boolean isSnapshot() {
            return isNonUniqueSnapshot() || isUniqueSnapshot();
        }

        public boolean isNonUniqueSnapshot() {
            return version.endsWith("-SNAPSHOT");
        }

        public boolean isUniqueSnapshot() {
            return uniqueSnapshotVersionMatcher.matches();
        }

        private String getNonUniqueSnapshot() {
            if (!isUniqueSnapshot()) {
                return isNonUniqueSnapshot() ? version : "";
            }
            return uniqueSnapshotVersionMatcher.replaceAll("$1-SNAPSHOT");
        }

        public String toString() {
            return group + ":" + artifactId + ":" + version + (classifier == null ? "" : ":" + classifier);
        }

        public static MavenArtifact from(String artifact) {
            String[] parts = artifact.split(":");
            String group = parts[0];
            String artifactId = parts[1];
            String version = parts[2];
            String classifier = parts.length == 4 ? parts[3] : null;

            return new MavenArtifact(group, artifactId, version, classifier, UNIQUE_SNAPSHOT.matcher(version));
        }
    }
}
