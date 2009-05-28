module('sipx');

sipx.header = function(spec) {
    var that = new Element('h3', {
        'class' : 'ui-widget ui-widget-content ui-corner-all ui-helper-reset',
        html : "User portal"
    });

    return that;
};

sipx.content = function(spec) {
    var that = new Element('div', {
        id : 'content'
    });

    var accordion = sprinkle.ui.accordion( {
        id : 'main-navigation'
    });
    var center = new Element('div', {
        "class" : 'ui-layout-center'
    });
    center.grab(accordion);

    var north = new Element('div', {
        "class" : 'ui-layout-north'
    });
    north.grab(sipx.header());

    [ north, center ].each( function(it) {
        it.inject(that);
    });

    /**
     * Everything that needs to happen once dom is in place should be added
     * here.
     *
     * For now we just call it in main, but there is probably an event that
     * could be used to register is as a callback.
     */
    function ondomready() {
        accordion.create();
    }

    $extend(that, {
        ondomready : ondomready
    });

    return that;
};

sipx.main = function() {
    // from now on '$' is for mootols and jQuery for jQuery...
    jQuery.noConflict();

    // initialize content
    var content = sipx.content();
    content.replaces('content');
    jQuery(document).ready( function() {
        content.ondomready();
    });
};

google.setOnLoadCallback(sipx.main);
