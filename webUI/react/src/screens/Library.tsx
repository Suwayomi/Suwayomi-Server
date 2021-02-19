/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { Tab, Tabs } from '@material-ui/core';
import React, { useContext, useEffect, useState } from 'react';
import MangaGrid from '../components/MangaGrid';
import NavBarTitle from '../context/NavbarTitle';

interface IMangaCategory {
    category: ICategory
    mangas: IManga[]
}

interface TabPanelProps {
    children: React.ReactNode;
    index: any;
    value: any;
}

function TabPanel(props: TabPanelProps) {
    const {
        children, value, index, ...other
    } = props;

    return (
        <div
            role="tabpanel"
            hidden={value !== index}
            id={`simple-tabpanel-${index}`}
            aria-labelledby={`simple-tab-${index}`}
            // eslint-disable-next-line react/jsx-props-no-spreading
            {...other}
        >
            {value === index && children}
        </div>
    );
}

export default function Library() {
    const { setTitle } = useContext(NavBarTitle);
    const [tabs, setTabs] = useState<IMangaCategory[]>([]);
    const [tabNum, setTabNum] = useState<number>(0);
    const [lastPageNum, setLastPageNum] = useState<number>(1);

    useEffect(() => {
        setTitle('Library');
    }, []);
    useEffect(() => {
        // eslint-disable-next-line no-var
        var newTabs: IMangaCategory[] = [];
        fetch('http://127.0.0.1:4567/api/v1/library')
            .then((response) => response.json())
            .then((data: IManga[]) => {
                // if some manga with no category exist, they will be added under a virtual category
                if (data.length > 0) {
                    newTabs = [
                        {
                            category: {
                                name: 'Default', isLanding: true, order: 0, id: 0,
                            },
                            mangas: data,
                        },
                    ]; // will set state on the next fetch
                }
            })
            .then(
                () => {
                    fetch('http://127.0.0.1:4567/api/v1/category')
                        .then((response) => response.json())
                        .then((data: ICategory[]) => {
                            const mangaCategories = data.map((category) => ({
                                category,
                                mangas: [] as IManga[],
                            }));
                            setTabs([...newTabs, ...mangaCategories]);
                        });
                },
            );
    }, []);
    // eslint-disable-next-line max-len
    const handleTabChange = (event: React.ChangeEvent<{}>, newValue: number) => setTabNum(newValue);

    let toRender;
    if (tabs.length > 1) {
        const tabDefines = tabs.map((tab) => (<Tab label={tab.category.name} />));

        const tabBodies = tabs.map((tab, index) => (
            <TabPanel value={tabNum} index={index}>
                <MangaGrid
                    mangas={tab.mangas}
                    hasNextPage={false}
                    lastPageNum={lastPageNum}
                    setLastPageNum={setLastPageNum}
                />
            </TabPanel>
        ));

        // 160px is min-width for viewport width of >600
        const scrollableTabs = window.innerWidth < tabs.length * 160;
        toRender = (
            <>
                <Tabs
                    value={tabNum}
                    onChange={handleTabChange}
                    indicatorColor="primary"
                    textColor="primary"
                    centered={!scrollableTabs}
                    variant={scrollableTabs ? 'scrollable' : 'fullWidth'}
                    scrollButtons="on"
                >
                    {tabDefines}
                </Tabs>
                {tabBodies}
            </>
        );
    } else {
        const mangas = tabs.length === 1 ? tabs[0].mangas : [];
        toRender = (
            <MangaGrid
                mangas={mangas}
                hasNextPage={false}
                lastPageNum={lastPageNum}
                setLastPageNum={setLastPageNum}
            />
        );
    }

    return toRender;
}
