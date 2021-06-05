/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ReactDOM from 'react-dom';
import React from 'react';
import Slide, { SlideProps } from '@material-ui/core/Slide';
import Snackbar from '@material-ui/core/Snackbar';
import MuiAlert, { Color as Severity } from '@material-ui/lab/Alert';

function removeToast(id: string) {
    const container = document.querySelector(`#${id}`)!!;
    ReactDOM.unmountComponentAtNode(container);
    document.body.removeChild(container);
}

function Transition(props: SlideProps) {
    // eslint-disable-next-line react/jsx-props-no-spreading
    return <Slide {...props} direction="up" />;
}

interface IToastProps{
    message: string
    severity: Severity
}

function Toast(props: IToastProps) {
    const { message, severity } = props;
    const [open, setOpen] = React.useState(true);

    const handleClose = () => {
        setOpen(false);
    };

    return (
        <Snackbar
            open={open}
            onClose={handleClose}
            autoHideDuration={3000}
            TransitionComponent={Transition}
            message="I love snacks"
        >
            <MuiAlert elevation={6} variant="filled" onClose={handleClose} severity={severity}>
                {message}
            </MuiAlert>
        </Snackbar>
    );
}

export default function makeToast(message: string, severity: Severity) {
    const id = Math.floor(Math.random() * 1000);
    const container = document.createElement('div');
    container.id = `alert-${id}`;

    document.body.appendChild(container);

    ReactDOM.render(<Toast message={message} severity={severity} />, container);

    setTimeout(() => removeToast(container.id), 3500);
}
