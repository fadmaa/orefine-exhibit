ExporterManager.MenuItems.push({});//add separator
ExporterManager.MenuItems.push(
		{
			"id" : "exportExhibit",
        	"label":"Exhibit",
        	"click": function() { exportExhibit(); }
		}
);

function exportExhibit() {
    var name = $.trim(theProject.metadata.name.replace(/\W/g, ' ')).replace(/\s+/g, '-');
	var allfacets = [];
	for(var i=0; i < ui.browsingEngine._facets.length; i++){
		var o = ui.browsingEngine._facets[i].facet.getJSON();
		o.selection = [];
		allfacets.push(o);
	}
	
	
    var form = document.createElement("form");
    $(form)
        .css("display", "none")
        .attr("method", "post")
        .attr("action", "command/core/export-rows/" + name + ".zip")
        .attr("target", "gridworks-export");

    $('<input />')
        .attr("name", "engine")
        .attr("value", JSON.stringify(ui.browsingEngine.getJSON()))
        .appendTo(form);
	
	$('<input />')
        .attr("name", "allfacets")
        .attr("value", JSON.stringify(allfacets))
        .appendTo(form);
		
    $('<input />')
        .attr("name", "project")
        .attr("value", theProject.id)
        .appendTo(form);
    $('<input />')
        .attr("name", "format")
        .attr("value", "exhibit")
        .appendTo(form);

    document.body.appendChild(form);

    window.open("about:blank", "gridworks-export");
    form.submit();

    document.body.removeChild(form);
};
