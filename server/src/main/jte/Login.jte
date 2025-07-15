@import suwayomi.tachidesk.i18n.MR
@import suwayomi.tachidesk.server.generated.BuildConfig

@param java.util.Locale locale
@param java.lang.String error

<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>${MR.strings.INSTANCE.getLogin_label_title().localized(locale)}</title>
    <style>
        * {
            box-sizing: border-box;
        }
        html, body {
            margin: 0;
            padding: 0;
            height: 100%;
        }
        body {
            display: flex;
            flex-direction: column;
            background-color: rgb(12, 16, 33);
            font-family: "Roboto","Helvetica","Arial",sans-serif;
            font-weight: 400;
            letter-spacing: 0em;
        }
        button[disabled], input[disabled] {
            cursor: not-allowed;
        }
        header {
            background-color: rgb(34, 38, 53);
            box-shadow: rgba(0, 0, 0, 0.2) 0px 2px 4px -1px, rgba(0, 0, 0, 0.14) 0px 4px 5px 0px, rgba(0, 0, 0, 0.12) 0px 1px 10px 0px;
            color: #fff;
            padding: 8px 32px;
        }
        header h1, header p {
            margin: 0;
        }
        footer {
            color: #fff;
            padding: 8px;
        }
        footer p {
            margin: 0;
            font-size: 0.7rem;
        }
        main {
            height: 100%;
        }
        main {
            position: relative;
            padding-top: 24px;
        }
        form {
            margin: 8px;
            padding: 8px 24px;
            border-radius: 8px;
            border: 1px solid rgb(12, 16, 33);
            background-color: rgb(6, 8, 16);
            color: white;
        }
        .error {
            margin: 8px;
            padding: 8px 16px;
            border-radius: 8px;
            border: 1px solid #b71c1c;
            background-color: #c62828;
            color: white;
        }
        .error:empty {
            display: none;
        }
        form label {
            cursor: pointer;
        }
        form button {
            all: unset;
            padding: 8px;
            line-height: 1.75;
            text-align: center;
            min-width: 64px;
            border-radius: 4px;
            padding: 6px 8px;
            color: rgb(91, 116, 239);
            text-transform: uppercase;
            letter-spacing: 0.02857em;
        }
        form button:not([disabled]) {
            cursor: pointer;
        }
        form button:not([disabled]):hover {
            background-color: rgba(91, 116, 239, 0.08);
        }
        form input {
            all: unset;
            border-radius: 4px;
            border: 1px solid rgba(255, 255, 255, 0.23);
            padding: 6px 12px;
            width: auto;
            min-width: 0;
        }
        form input:hover {
            border-color: white;
        }
        form input:focus {
            border-color: rgb(91, 116, 239);
        }
        form .controls {
            display: grid;
            align-items: center;
            grid-template-columns: 1fr;
        }
        form .controls > :nth-child(even):not(:last-child) {
            margin-bottom: 6px;
        }
        form .submit {
            display: flex;
            flex-direction: column;
            align-items: center;
            margin-top: 24px;
        }

        @media (min-width: 500px) {
            form {
                width: 100%;
                max-width: 450px;
                margin: 8px auto;
            }
            .error {
                width: 100%;
                max-width: 450px;
                margin: 8px auto;
            }
            form .controls {
                grid-template-columns: auto 1fr;
                column-gap: 16px;
                row-gap: 6px;
            }
            form .controls > :nth-child(even):not(:last-child) {
                margin-bottom: 0px;
            }
        }
    </style>
</head>
<body>
    <header>
        <h1>Suwayomi</h1>
    </header>
    <main>
        <div class="error">${error}</div>
        <form method="POST">
            <h2>Login</h2>
            <div class="controls">
                <label for="user">${MR.strings.INSTANCE.getLogin_label_username().localized(locale)}:</label>
                    <input type="text" name="user" id="user" required placeholder="${MR.strings.INSTANCE.getLogin_placeholder_username().localized(locale)}"/>
                    <label for="pass">${MR.strings.INSTANCE.getLogin_label_password().localized(locale)}:</label>
                    <input type="password" name="pass" id="pass" required placeholder="${MR.strings.INSTANCE.getLogin_placeholder_password().localized(locale)}"/>
            </div>
            <div class="submit">
                <button type="submit">${MR.strings.INSTANCE.getLogin_label_login().localized(locale)}</button>
            </div>
        </form>
    </main>
    <footer>
        <p>Suwayomi: ${MR.strings.INSTANCE.getLabel_version().localized(locale, BuildConfig.VERSION)}</p>
    </footer>
</body>
</html>
