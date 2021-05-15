/* eslint-disable @typescript-eslint/no-unused-vars */
import { makeStyles } from '@material-ui/core/styles';
import React, { useEffect } from 'react';
import Page from './Page';

const useStyles = makeStyles({
    reader: {
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'center',
        margin: '0 auto',
        width: '100%',
        height: '100vh',
    },
});

interface IProps {
    pages: Array<IReaderPage>
    setCurPage: React.Dispatch<React.SetStateAction<number>>
    curPage: number
    settings: IReaderSettings
}

export default function SinglePageReader(props: IProps) {
    const {
        pages, settings, setCurPage, curPage,
    } = props;

    const classes = useStyles();

    function nextPage() {
        if (curPage < pages.length - 1) { setCurPage(curPage + 1); }
    }

    function prevPage() {
        if (curPage > 0) { setCurPage(curPage - 1); }
    }

    function keyboardControl(e:KeyboardEvent) {
        switch (e.key) {
            case 'ArrowRight':
                nextPage();
                break;
            case 'ArrowLeft':
                prevPage();
                break;
            default:
                break;
        }
    }

    function clickControl(e:MouseEvent) {
        if (e.clientX > window.innerWidth / 2) {
            nextPage();
        } else {
            prevPage();
        }
    }

    useEffect(() => {
        document.addEventListener('keyup', keyboardControl, false);
        document.addEventListener('click', clickControl);

        return () => {
            document.removeEventListener('keyup', keyboardControl);
            document.removeEventListener('click', clickControl);
        };
    }, [curPage]);

    return (
        <div className={classes.reader}>
            <Page
                key={curPage}
                index={curPage}
                src={pages[curPage].src}
                setCurPage={setCurPage}
                settings={settings}
            />
        </div>
    );
}
