package hudson.plugins.ghd;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.egit.github.core.Download;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.DownloadService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.s3.internal.Mimetypes;

/**
 * 
 * Profile used in global Jenkins settings.
 * 
 * @author lukasz.nowak@homersoft.com
 *
 */
public class GitHubProfile {
	
	/**
	 * Name of the profile.
	 */
    private String name;
    
    /**
     * Login to GitHub.
     */
    private String user;
    
    /**
     * Password to GitHub.
     */
    private String password;
    
    /**
     * Organization if used.
     */
    private String organization;
    
    /**
     * Client to GitHub.
     */
    private static GitHubClient CLIENT;

    
    public GitHubProfile() {
    }

    @DataBoundConstructor
    public GitHubProfile(String name, String user, String password, String organization) {
        this.name = name;
        this.user = user;
        this.password = password;
        this.organization = organization;
        
        CLIENT = new GitHubClient();
        CLIENT.setCredentials(user, password);
    }

    public final String getAccessKey() {
        return user;
    }

    public final void setAccessKey(String accessKey) {
        this.user = accessKey;
    }

    public final String getSecretKey() {
        return password;
    }

    public void setSecretKey(String secretKey) {
        this.password = secretKey;
    }

    public final String getName() {
        return this.name;
    }

    public final void setName(String name) {
        this.name = name;
    }
    
    /**
	 * @return the organization
	 */
	public final String getOrganization() {
		return organization;
	}

	/**
	 * @param organization the organization to set
	 */
	public final void setOrganization(String organization) {
		this.organization = organization;
	}

	public GitHubClient getClient() {
        if (CLIENT == null) {
        	CLIENT = new GitHubClient();
            CLIENT.setCredentials(user, password);
        }
        return CLIENT;
    }

    public void check() throws Exception {
        RepositoryService service = new RepositoryService(getClient());
        service.getRepositories();
    }  
   
    public void upload(String repositoryName, FilePath filePath) throws IOException, InterruptedException {
        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }
        
        try {
	        Repository repository = getRepository(repositoryName);
	        
			File file = new File(filePath.toURI());
			
			Download download = new Download();
			download.setName(file.getName());
			download.setSize(file.length());		
			download.setContentType(Mimetypes.getInstance().getMimetype(filePath.getName()));
			
			DownloadService downloadService = new DownloadService(getClient());					
			downloadService.createDownload(repository, download, file);               
        } catch (Exception e) {
        	throw new IOException("Exception while uploading file " + filePath + " repository " + repositoryName, e);
        }
    }
    
    /**
     * Gets repository from GitHub API.
     * 
     * @param repositoryName		repository name
     * @return						returned repository object
     * @throws Exception
     */
    private Repository getRepository(String repositoryName) throws Exception {
    	Repository repository = null;
    	
    	try {	    	
			RepositoryService repositoryService = new RepositoryService(getClient());
			
			List<Repository> repositories = null;
			if (organization != null && !organization.isEmpty()) {
				repositories = repositoryService.getOrgRepositories(organization);
			} else {
				repositories = repositoryService.getRepositories();
			}
			
			for (Repository temp : repositories) {
				if (temp.getName().equals(repositoryName)) {
					repository = temp;
					break;
				}
			}	
    	} catch (IOException e) {
    		throw new IOException("Exception while getting repository " + repositoryName, e);
    	}
    	
    	return repository;
    }
}
