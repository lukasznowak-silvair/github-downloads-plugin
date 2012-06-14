package hudson.plugins.ghd;

public final class Entry {
    /**
     * Destination repository for the upload. 
     */
    public String destinationRepository;
    /**
     * File name relative to the workspace root to upload.
     * Can contain macros and wildcards.
     * <p>
     */
    public String sourceFile;
}
