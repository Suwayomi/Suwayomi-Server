// https://github.com/microlinkhq/browserless/blob/master/packages/goto/src/evasions/navigator-permissions.js
if (!window.Notification) {
    window.Notification = {
        permission: 'denied'
    }
}
const originalQuery = window.navigator.permissions.query
window.navigator.permissions.__proto__.query = parameters =>
    parameters.name === 'notifications'
        ? Promise.resolve({state: window.Notification.permission})
        : originalQuery(parameters)
const oldCall = Function.prototype.call

function call() {
    return oldCall.apply(this, arguments)
}

Function.prototype.call = call
const nativeToStringFunctionString = Error.toString().replace(/Error/g, 'toString')
const oldToString = Function.prototype.toString

function functionToString() {
    if (this === window.navigator.permissions.query) {
        return 'function query() { [native code] }'
    }
    if (this === functionToString) {
        return nativeToStringFunctionString
    }
    return oldCall.call(oldToString, this)
}

// eslint-disable-next-line
Function.prototype.toString = functionToString
