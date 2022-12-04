(function () {
    const ORIGINAL_CANVAS = HTMLCanvasElement.prototype[name];
    Object.defineProperty(HTMLCanvasElement.prototype, name, {
        "value": function () {
            var shift = {
                'r': Math.floor(Math.random() * 10) - 5,
                'g': Math.floor(Math.random() * 10) - 5,
                'b': Math.floor(Math.random() * 10) - 5,
                'a': Math.floor(Math.random() * 10) - 5
            };
            var width = this.width,
                height = this.height,
                context = this.getContext("2d");
            var imageData = context.getImageData(0, 0, width, height);
            for (var i = 0; i < height; i++) {
                for (var j = 0; j < width; j++) {
                    var n = ((i * (width * 4)) + (j * 4));
                    imageData.data[n + 0] = imageData.data[n + 0] + shift.r;
                    imageData.data[n + 1] = imageData.data[n + 1] + shift.g;
                    imageData.data[n + 2] = imageData.data[n + 2] + shift.b;
                    imageData.data[n + 3] = imageData.data[n + 3] + shift.a;
                }
            }
            context.putImageData(imageData, 0, 0);
            return ORIGINAL_CANVAS.apply(this, arguments);
        }
    });
})(this);
