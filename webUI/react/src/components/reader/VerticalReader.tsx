/* eslint-disable @typescript-eslint/no-unused-vars */
import { makeStyles } from '@material-ui/core/styles';
import React, { useEffect } from 'react';
import Page from './Page';

const useStyles = makeStyles({
    reader: {
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        margin: '0 auto',
        width: '100%',
    },
});

export default function VerticalReader(props: IReaderProps) {
    const {
        pages, settings, setCurPage, manga, chapter,
    } = props;

    const classes = useStyles();
    const handleScroll = () => {
        if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight) {
            window.location.href = `/manga/${manga.id}/chapter/${chapter.index + 1}`;
        }
    };
    useEffect(() => {
        window.addEventListener('scroll', handleScroll);

        return () => {
            window.removeEventListener('scroll', handleScroll);
        };
    }, []);

    return (
        <div className={classes.reader}>
            {
                pages.map((page) => (
                    <Page
                        key={page.index}
                        index={page.index}
                        src={page.src}
                        setCurPage={setCurPage}
                        settings={settings}
                    />
                ))
            }
        </div>
    );
}
