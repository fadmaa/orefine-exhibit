/*
 * Function invoked to initialize the extension.
 */
function init() {
	var RefineServlet = Packages.com.google.refine.RefineServlet;
	
    /*
     *  Exporter
     */
    var ExporterRegistry = Packages.com.google.refine.exporters.ExporterRegistry;
	var ExhibitExporter = Packages.me.fadmaa.orefine.exhibit.ExhibitExporter;
    ExporterRegistry.registerExporter("exhibit", new ExhibitExporter());
       
    /*
     *  Client-side Resources
     */
    var ClientSideResourceManager = Packages.com.google.refine.ClientSideResourceManager;
    
    // Script files to inject into /project page
    ClientSideResourceManager.addPaths(
        "project/scripts",
        module,
        [
            "scripts/init.js"
		]
    );
    
    // Style files to inject into /project page
    ClientSideResourceManager.addPaths(
        "project/styles",
        module,
        [
        ]
    );
    
}

function process(path, request, response) {
    // Analyze path and handle this request yourself.
    if (path == "/" || path == "") {
    	butterfly.redirect(request, response, "index.html");
    }
}