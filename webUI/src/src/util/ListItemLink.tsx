/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React from 'react';
import ListItem, { ListItemProps } from '@material-ui/core/ListItem';

export default function ListItemLink(props: ListItemProps<'a', { button?: true }>) {
    // eslint-disable-next-line react/jsx-props-no-spreading
    return <ListItem button component="a" {...props} />;
}
