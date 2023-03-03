(function () {
    var main = {
        tabsWrapper: ['#medical', '#other'],
        titlesTmpl: '<div class="person title">' +
            '<span class="person-info"><strong>Name</strong></span>' +
            '<span class="person-info"><strong>Preferable days</strong></span>' +
            '<span class="person-info"><strong>Avaliability time</strong></span>' +
            '<span class="person-info"><strong>Phone</strong></span>' +
            '<span class="person-info"><strong>Security</strong></span>' +
            '<span class="person-info"><strong>Telegram</strong></span>' +
            '</div>',
        generalTitlesTmpl: '<div class="person title">' +
            '<span class="person-info"><strong>Name</strong></span>' +
            '<span class="person-info"><strong>Preferable days</strong></span>' +
            '<span class="person-info"><strong>Avaliability time</strong></span>' +
            '<span class="person-info"><strong>Phone</strong></span>' +
            '<span class="person-info"><strong>Security</strong></span>' +
            '<span class="person-info"><strong>Telegram</strong></span>' +
            '<span class="person-info"><strong>Date</strong></span>' +
            '</div>',

        init: function () {
            this.tabs = $(".tabs-wrapper");
            this.calendar = $("#calendar");
            this.popup = $("#popup");
            this.body = $('body');

            $.ajax({
                url: "/all",
                beforeSend: function(xhr) {
                    xhr.setRequestHeader('Access-Control-Allow-Origin', 'Accept');
                }
            }).done(function(data) {
                if (data) {
                    this.data = data;
                    this.initWidgets();
                    this.initGeneralTable();
                }
            }.bind(this));
        },

        initWidgets: function () {
            this.popup.dialog({
                autoOpen: false,
                draggable: false,
                resizable: false,
                modal: true,
                closeText: "",
                close: function () {
                    this.body.removeClass('popup-open')
                }.bind(this)
            });

            this.tabs.tabs();

            this.calendar.zabuto_calendar({
                action: function (event) {
                    var date = $(event.currentTarget).data("date");
                    this.setHtmlToTabs(this.getHtmlForDate(date), {
                        "medical": "#medical",
                        "other": "#other"
                    });
                    this.popup.dialog("option", "title", date);
                    this.body.addClass('popup-open')
                    this.popup.dialog("open");
                    return true;
                }.bind(this)
            });
        },

        getHtmlForDate: function (date, template) {
            var tabsHtml = {
                "medical": "",
                "other": "",
                "all": ""
            };

            this.data.forEach(function (element) {

                if (!date || date >= element.date) {
                    var item = '<div class="person">' +
                        '<span class="person-info"><strong class="person-info-title">Name</strong>' + element.name + '</span>' +
                        '<span class="person-info"><strong class="person-info-title">Preferable days</strong>' + element.days + '</span>' +
                        '<span class="person-info"><strong class="person-info-title">Avaliability time</strong>' + element.time + '</span>' +
                        '<span class="person-info"><strong class="person-info-title">Phone</strong>' + element.phone + '</span>' +
                        '<span class="person-info"><strong class="person-info-title">Security</strong>' + element.keeper + '</span>' +
                        '<span class="person-info"><strong class="person-info-title">Telegram</strong>' + element.telegram + '</span>';

                    if (!date) {
                        item += '<span class="person-info"><strong class="person-info-title">Date</strong>' + element.date + '</span>' +
                            '</div>'
                    }

                    item +='</div>'

                    tabsHtml[element.volunteer] += item;
                    tabsHtml["all"] += item;
                } else {
                    return false;
                }
            });

            this.setColumnsTitle(tabsHtml, template ? template : this.titlesTmpl);

            return tabsHtml;
        },

        setColumnsTitle: function (tabsHtml, template) {
            $.each(tabsHtml, function (key, html) {
                if (html.length) {
                    tabsHtml[key] = template + html;
                }
            }.bind(this))
        },

        setHtmlToTabs: function (tabsHtml, elements) {
            this.cleanWrapper();

            $.each(tabsHtml, function (key, html) {
                if (html.length) {
                    $(elements[key]).html(html)
                }
            })
        },

        cleanWrapper: function () {
            this.tabsWrapper.forEach(function ( element) {
                $(element).html("<h3>No volunteers in this day</h3>")
            })
        },

        initGeneralTable: function () {
            this.setHtmlToTabs(this.getHtmlForDate(false, this.generalTitlesTmpl), {
                "all": "#general-table-all",
                "medical": "#general-table-medical",
                "other": "#general-table-other"
            });
        }
    }

    main.init();
}());