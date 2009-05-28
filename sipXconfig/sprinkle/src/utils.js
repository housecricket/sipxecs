/**
 * Use to define a name space
 *
 * module("a.b.c.d") - will recursively define a = { b : { c : { d : {} } } };
 *
 * @param modulename
 */
function module(modulename) {
    var split = modulename.split('.', 2);
    var head = split[0], reminder = split[1];
    if (undefined === this[head]) {
        this[head] = {};
    }
    if (reminder) {
        module.apply(this[head], [ reminder ]);
    }
}
