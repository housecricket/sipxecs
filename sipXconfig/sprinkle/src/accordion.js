/**
 * Accordion for sprinkle (sipXecs user portal)
 */

module('sprinkle.ui');
module('sprinkle.data');

sprinkle.data = {
    forwarding : [ {
        number : '1234567',
        delay : 30,
        type : 'immediate'
    }, {
        number : '23312',
        delay : 20,
        type : 'immediate'
    }, {
        number : '22334455',
        delay : 45,
        type : 'immediate'
    }, {
        number : 'user@abd.com',
        delay : 30,
        type : 'immediate'
    } ]
};

sprinkle.ui.forwarding = function(spec) {
    var that = new Element('ul', {
        id : 'my-forwarding',
        'class' : 'sortable'
    });

    // <li class="ui-state-default"><span class="ui-icon
    // ui-icon-arrowthick-2-n-s"></span>Item 7</li>
    function addItem(data) {
        var li = new Element('li', {
            'class' : 'ui-state-default'
        });
        var icon = new Element('span', {
            'class' : 'ui-icon ui-icon-arrowthick-2-n-s'
        });
        var text = new Element('div', {
            html : [ 'Call:', data.number, 'ring for', data.delay ].join(' ')
        });
        li.grab(icon);
        li.grab(text);
        that.grab(li);
    }

    spec.items.each( function(it) {
        addItem(it);
    });

    return that;
};

sprinkle.ui.tabs = function(spec) {
    var that = new Element('div', {
        id : 'my-config-pane'
    });
    var links = new Element('ul');

    var forwarding = sprinkle.ui.forwarding( {
        items : sprinkle.data.forwarding
    });

    function addTab(title, element) {
        var li = new Element('li');
        var link = new Element('a', {
            href : '#' + element.get('id'),
            html : title
        });
        li.grab(link);
        links.grab(li);
        that.grab(element);
        return element;
    }

    function create() {
        jQuery(that).tabs();
        jQuery(forwarding).sortable();
    }

    that.grab(links);
    addTab("My Settings", new Element('div', {
        id : 'settings',
        html : 'settings tab'
    }));
    var tab2 = addTab("My Forwarding", new Element('div', {
        id : 'forwarding',
        html : 'forwarding tab'
    }));
    tab2.grab(forwarding);

    $extend(that, {
        create : create
    });
    return that;
};

sprinkle.ui.accordionHeader = function(spec) {
    var that = new Element('h3');
    that.grab(new Element('a', spec));
    return that;
};

sprinkle.ui.accordion = function(spec) {
    var that = new Element('div', spec);
    var settings = sprinkle.ui.tabs();

    function create() {
        settings.create();
        jQuery(that).accordion( {
            autoHeight: false
            //fillSpace : true
        });
    }

    var items = [ {
        header : {
            html : 'My PhoneBook'
        },
        content : {
            html : 'phonebook goes here'
        }
    }, {
        header : {
            html : 'My Voicemail & Call History'
        },
        content : {
            html : 'voicemails and calls go here'
        }
    }, {
        header : {
            html : 'My Conferences'
        },
        content : {
            html : 'conferences go here'
        }
    }, {
        header : {
            html : 'My Settings'
        },
        content : settings
    } ];

    items.each( function(it) {
        var content, header = sprinkle.ui.accordionHeader(it.header);

        if ('element' === $type(it.content)) {
            content = it.content;
        } else {
            content = new Element('div', it.content);
        }

        that.grab(header);
        that.grab(content);
    });

    $extend(that, {
        create : create
    });

    return that;
};
