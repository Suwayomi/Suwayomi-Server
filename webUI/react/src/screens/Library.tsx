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
        children, value, index,
    } = props;

    return (
        <div
            role="tabpanel"
            hidden={value !== index}
            id={`simple-tabpanel-${index}`}
        >
            {value === index && children}
        </div>
    );
}

export default function Library() {
    const { setTitle } = useContext(NavBarTitle);
    const [tabs, setTabs] = useState<IMangaCategory[]>([]);
    const [tabNum, setTabNum] = useState<number>(0);

    // a hack so MangaGrid doesn't stop working. I won't change it in case
    // if I do manga pagination for library..
    const [lastPageNum, setLastPageNum] = useState<number>(1);
    useEffect(() => {
        setTitle('Library');
    }, []);

    // eslint-disable-next-line @typescript-eslint/no-shadow
    const fetchAndSetMangas = (tabs: IMangaCategory[], tab: IMangaCategory, index: number) => {
        fetch(`http://127.0.0.1:4567/api/v1/category/${tab.category.id}`)
            .then((response) => response.json())
            .then((data: IManga[]) => {
                const tabsClone = JSON.parse(JSON.stringify(tabs));
                tabsClone[index].mangas = data;
                setTabs(tabsClone); // clone the object
            });
    };

    const handleTabChange = (newTab: number) => {
        setTabNum(newTab);
        tabs.forEach((tab, index) => {
            if (tab.category.order === newTab && tab.mangas.length === 0) {
                // mangas are empty, fetch the mangas
                fetchAndSetMangas(tabs, tab, index);
            }
        });
    };

    useEffect(() => {
        fetch('http://127.0.0.1:4567/api/v1/library')
            .then((response) => response.json())
            .then((data: IManga[]) => {
                // if some manga with no category exist, they will be added under a virtual category
                if (data.length > 0) {
                    return [
                        {
                            category: {
                                name: 'Default', isLanding: true, order: 0, id: -1,
                            },
                            mangas: data,
                        },
                    ]; // will set state on the next fetch
                }

                // no default category so the first tab is 1
                setTabNum(1);
                return [];
            })
            .then(
                (newTabs: IMangaCategory[]) => {
                    fetch('http://127.0.0.1:4567/api/v1/category')
                        .then((response) => response.json())
                        .then((data: ICategory[]) => {
                            const mangaCategories = data.map((category) => ({
                                category,
                                mangas: [] as IManga[],
                            }));
                            const newNewTabs = [...newTabs, ...mangaCategories];
                            setTabs(newNewTabs);

                            // if no default category, we must fetch the first tab now...
                            // eslint-disable-next-line max-len
                            if (newTabs.length === 0) { fetchAndSetMangas(newNewTabs, newNewTabs[0], 0); }
                        });
                },
            );
    }, []);

    let toRender;
    if (tabs.length > 1) {
        // eslint-disable-next-line max-len
        const tabDefines = tabs.map((tab) => (<Tab label={tab.category.name} value={tab.category.order} />));

        const tabBodies = tabs.map((tab) => (
            <TabPanel value={tabNum} index={tab.category.order}>
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
                    onChange={(e, newTab) => handleTabChange(newTab)}
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
