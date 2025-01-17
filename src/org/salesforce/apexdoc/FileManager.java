package org.salesforce.apexdoc;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileManager {
    FileOutputStream fos;
    DataOutputStream dos;
    String path;
    public String header;
    public String APEX_DOC_PATH = "";
    public StringBuffer infoMessages;

    public FileManager() {
        infoMessages = new StringBuffer();
    }

    private static String escapeHTML(String s) {
        StringBuilder out = new StringBuilder(Math.max(16, s.length()));
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&') {
                out.append("&#");
                out.append((int) c);
                out.append(';');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public FileManager(String path) {
        infoMessages = new StringBuffer();

        if (path == null || path.trim().length() == 0)
            this.path = ".";
        else
            this.path = path;
    }

    private boolean createHTML(TreeMap<String, String> mapFNameToContent, IProgressMonitor monitor) {
        try {
            (new File(path)).mkdirs();

            for (String fileName : mapFNameToContent.keySet()) {
                String contents = mapFNameToContent.get(fileName);
                fileName = path + "/" + fileName + ".html";
                File file = new File(fileName);
                fos = new FileOutputStream(file);
                dos = new DataOutputStream(fos);
                dos.write(contents.getBytes());
                dos.close();
                fos.close();
                infoMessages.append(fileName + " Processed...\n");
                System.out.println(fileName + " Processed...");
                if (monitor != null)
                    monitor.worked(1);
            }
            copy(path);
            return true;
        } catch (Exception e) {

            e.printStackTrace();
        }

        return false;
    }

    private String strLinkfromModel(ApexModel model, String strClassName, String hostedSourceURL) {
        return "<a target='_blank' class='hostedSourceLink' href='" + hostedSourceURL + strClassName + ".cls#L"
                + model.getInameLine() + "'>";
    }

    /********************************************************************************************
     * @description main routine that creates an HTML file for each class specified
     * @param mapGroupNameToClassGroup
     * @param cModels
     * @param projectDetail
     * @param homeContents
     * @param hostedSourceURL
     * @param monitor
     */
    private void makeFile(TreeMap<String, ClassGroup> mapGroupNameToClassGroup, ArrayList<ClassModel> cModels,  String projectDetail, String homeContents, String hostedSourceURL, IProgressMonitor monitor) {
        String links = "";
        String fileName = "";
        TreeMap<String, String> mapFNameToContent = new TreeMap<String, String>();
        mapFNameToContent.put("index", homeContents);

        // create our Class Group content files
        createClassGroupContent(mapFNameToContent, links, projectDetail, mapGroupNameToClassGroup, cModels, monitor);

        for (ClassModel cModel : cModels) {
            String contents = links;
            if (cModel.getNameLine() != null && cModel.getNameLine().length() > 0) {
                fileName = cModel.getClassName();
                contents += "<td class='contentTD'>";

                contents += htmlForClassModel(cModel, hostedSourceURL);

                // deal with any nested classes
                for (ClassModel cmChild : cModel.getChildClassesSorted()) {
                    contents += "<p/>";
                    contents += htmlForClassModel(cmChild, hostedSourceURL);
                }

            } else {
                continue;
            }
            contents += "</div>";
            
            mapFNameToContent.put(fileName, contents);
            if (monitor != null)
                monitor.worked(1);
        }
        createHTML(mapFNameToContent, monitor);
    }

    /*********************************************************************************************
     * @description creates the HTML for the provided class, including its
     *              property and methods
     * @param cModel
     * @param hostedSourceURL
     * @return html string
     */
    private String htmlForClassModel(ClassModel cModel, String hostedSourceURL) {
        String contents = "";
        contents += "<h2 class='section-title'>" +
                strLinkfromModel(cModel, cModel.getTopmostClassName(), hostedSourceURL) +
                cModel.getClassName() + "</a>" +
                "</h2>";

        contents += "<div class='classSignature'>" +
                strLinkfromModel(cModel, cModel.getTopmostClassName(), hostedSourceURL) +
                escapeHTML(cModel.getNameLine()) + "</a></div>";

        if (cModel.getDescription() != "")
            contents += "<div class='classDetails'>" + escapeHTML(cModel.getDescription());
        if (cModel.getAuthor() != "")
            contents += "<br/><br/>" + escapeHTML(cModel.getAuthor());
        if (cModel.getDate() != "")
            contents += "<br/>" + escapeHTML(cModel.getDate());
        contents += "</div><p/>";

        if (cModel.getProperties().size() > 0) {
            // start Properties
            contents +=
                    "<h2 class='subsection-title'>Properties</h2>" +
                            "<div class='subsection-container'> " +
                            "<table class='properties' > ";

            for (PropertyModel prop : cModel.getPropertiesSorted()) {
                contents += "<tr class='propertyscope" + prop.getScope() + "'><td class='clsPropertyName'>" +
                        prop.getPropertyName() + "</td>";
                contents += "<td><div class='clsPropertyDeclaration'>" +
                        strLinkfromModel(prop, cModel.getTopmostClassName(), hostedSourceURL) +
                        escapeHTML(prop.getNameLine()) + "</a></div>";
                contents += "<div class='clsPropertyDescription'>" + escapeHTML(prop.getDescription()) + "</div></tr>";
            }
            // end Properties
            contents += "</table></div><p/>";
        }

        if (cModel.getMethods().size() > 0) {
            // start Methods
            contents +=
                    "<h2 class='subsection-title'>Methods</h2>" +
                            "<div class='subsection-container'> ";

            // method Table of Contents (TOC)
            contents += "<ul class='methodTOC'>";
            for (MethodModel method : cModel.getMethodsSorted()) {
                contents += "<li class='methodscope" + method.getScope() + "' >";
                contents += "<a class='methodTOCEntry' href='#" + method.getMethodName() + "'>"
                        + method.getMethodName() + "</a>";
                if (method.getDescription() != "")
                    contents += "<div class='methodTOCDescription'>" + method.getDescription() + "</div>";
                contents += "</li>";
            }
            contents += "</ul>";

            // full method display
            for (MethodModel method : cModel.getMethodsSorted()) {
                contents += "<div class='methodscope" + method.getScope() + "' >";
                contents += "<h2 class='methodHeader'><a id='" + method.getMethodName() + "'/>"
                        + method.getMethodName() + "</h2>" +
                        "<div class='methodSignature'>" +
                        strLinkfromModel(method, cModel.getTopmostClassName(), hostedSourceURL) +
                        escapeHTML(method.getNameLine()) + "</a></div>";

                if (method.getDescription() != "")
                    contents += "<div class='methodDescription'>" + escapeHTML(method.getDescription()) + "</div>";

                if (method.getParams().size() > 0) {
                    contents += "<div class='methodSubTitle'>Parameters</div>";
                    for (String param : method.getParams()) {
                        param = escapeHTML(param);
                        if (param != null && param.trim().length() > 0) {
                            Pattern p = Pattern.compile("\\s");
                            Matcher m = p.matcher(param);

                            String paramName;
                            String paramDescription;
                            if (m.find()) {
                            	int ich = m.start();
                                paramName = param.substring(0, ich);
                                paramDescription = param.substring(ich + 1);
                            } else {
                                paramName = param;
                                paramDescription = null;
                            }
                            contents += "<div class='paramName'>" + paramName + "</div>";

                            if (paramDescription != null)
                                contents += "<div class='paramDescription'>" + paramDescription + "</div>";
                        }
                    }
                    // end Parameters
                }

                if (method.getReturns() != "") {
                    contents += "<div class='methodSubTitle'>Return Value</div>";
                    contents += "<div class='methodReturns'>" + escapeHTML(method.getReturns()) + "</div>";
                }

                if (method.getExample() != "") {
                    contents += "<div class='methodSubTitle'>Example</div>";
                    contents += "<code class='methodExample'>" + escapeHTML(method.getExample()) + "</code>";
                }

                if (method.getAuthor() != "") {
                    contents += "<div class='methodSubTitle'>Author</div>";
                    contents += "<div class='methodReturns'>" + escapeHTML(method.getAuthor()) + "</div>";
                }

                if (method.getDate() != "") {
                    contents += "<div class='methodSubTitle'>Date</div>";
                    contents += "<div class='methodReturns'>" + escapeHTML(method.getDate()) + "</div>";
                }

                // end current method
                contents += "</div>";
            }
            // end all methods
            contents += "</div>";
        }

        return contents;
    }

    // create our Class Group content files
    private void createClassGroupContent(TreeMap<String, String> mapFNameToContent, String links, String projectDetail,
            TreeMap<String, ClassGroup> mapGroupNameToClassGroup,
            ArrayList<ClassModel> cModels, IProgressMonitor monitor) {

        for (String strGroup : mapGroupNameToClassGroup.keySet()) {
            ClassGroup cg = mapGroupNameToClassGroup.get(strGroup);
            if (cg.getContentSource() != null) {
                String cgContent = parseHTMLFile(cg.getContentSource());
                if (!cgContent.equals("")) {
                    String strHtml = Constants.getHeader(projectDetail) + links + "<td class='contentTD'>" +
                            "<h2 class='section-title'>" +
                            escapeHTML(cg.getName()) + "</h2>" + cgContent + "</td>";
                    strHtml += Constants.FOOTER;
                    mapFNameToContent.put(cg.getContentFilename(), strHtml);
                    if (monitor != null)
                        monitor.worked(1);
                }
            }
        }
    }
    
    private void docopy(String source, String target) throws Exception {

        InputStream is = this.getClass().getResourceAsStream(source);
        // InputStreamReader isr = new InputStreamReader(is);
        // BufferedReader reader = new BufferedReader(isr);
        FileOutputStream to = new FileOutputStream(target + "/" + source);

        byte[] buffer = new byte[4096];
        int bytesRead;

        while ((bytesRead = is.read(buffer)) != -1) {
            to.write(buffer, 0, bytesRead); // write
        }

        to.flush();
        to.close();
        is.close();
    }

    private void copy(String toFileName) throws IOException, Exception {
        docopy("apex_doc_logo.png", toFileName);
        docopy("ApexDoc.css", toFileName);
        docopy("ApexDoc.js", toFileName);
        docopy("CollapsibleList.js", toFileName);
        docopy("jquery-1.11.1.js", toFileName);
        docopy("toggle_block_btm.gif", toFileName);
        docopy("toggle_block_stretch.gif", toFileName);

    }

    public ArrayList<File> getFiles(String path) {
        File folder = new File(path);
        ArrayList<File> listOfFilesToCopy = new ArrayList<File>();
        if (folder != null) {
            File[] listOfFiles = folder.listFiles();
            if (listOfFiles != null && listOfFiles.length > 0) {
                for (int i = 0; i < listOfFiles.length; i++) {
                    if (listOfFiles[i].isFile()) {
                        listOfFilesToCopy.add(listOfFiles[i]);
                    }
                }
            } else {
                System.out.println("WARNING: No files found in directory: " + path);
            }
        }
        return listOfFilesToCopy;
    }

    public void createDoc(TreeMap<String, ClassGroup> mapGroupNameToClassGroup, ArrayList<ClassModel> cModels,
            String projectDetail, String homeContents, String hostedSourceURL, IProgressMonitor monitor) {
        makeFile(mapGroupNameToClassGroup, cModels, projectDetail, homeContents, hostedSourceURL, monitor);
    }

    private String parseFile(String filePath) {
        try {
            if (filePath != null && filePath.trim().length() > 0) {
                FileInputStream fstream = new FileInputStream(filePath);
                // Get the object of DataInputStream
                DataInputStream in = new DataInputStream(fstream);
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String contents = "";
                String strLine;

                while ((strLine = br.readLine()) != null) {
                    // Print the content on the console
                    strLine = strLine.trim();
                    if (strLine != null && strLine.length() > 0) {
                        contents += strLine;
                    }
                }
                // System.out.println("Contents = " + contents);
                br.close();
                return contents;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public String parseHTMLFile(String filePath) {

        String contents = (parseFile(filePath)).trim();
        if (contents != null && contents.length() > 0) {
            int startIndex = contents.indexOf("<body>");
            int endIndex = contents.indexOf("</body>");
            if (startIndex != -1) {
                if (contents.indexOf("</body>") != -1) {
                    contents = contents.substring(startIndex, endIndex);
                    return contents;
                }
            }
        }
        return "";
    }

}
